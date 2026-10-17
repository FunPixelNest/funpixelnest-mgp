// TEST ONLY — offline test of functions/send-push with every network call mocked.
// Run (from any folder without a node_modules directory):
//   SUPABASE_URL=http://supabase.test SUPABASE_SERVICE_ROLE_KEY=srk \
//     deno run --allow-env --allow-read --allow-net --node-modules-dir=none \
//     <path>/supabase/tests/send_push_test.ts
// Checks: auth rejection, RS256 JWT signed with the service-account key and
// accepted by the mock OAuth server, token caching, FCM payload shape, data
// stringified, dead tokens (404 / UNREGISTERED / INVALID_ARGUMENT token) deleted,
// other errors kept.

function assert(cond: unknown, label: string) {
  if (!cond) throw new Error("TEST FAILED: " + label);
  console.log("ok ", label);
}

const b64url = (buf: ArrayBuffer) =>
  btoa(String.fromCharCode(...new Uint8Array(buf))).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
const b64urlDecode = (s: string) =>
  Uint8Array.from(atob(s.replace(/-/g, "+").replace(/_/g, "/") + "===".slice((s.length + 3) % 4)), (c) => c.charCodeAt(0));

// --- Fake Firebase service account with a real RSA key ----------------------
const keyPair = await crypto.subtle.generateKey(
  { name: "RSASSA-PKCS1-v1_5", modulusLength: 2048, publicExponent: new Uint8Array([1, 0, 1]), hash: "SHA-256" },
  true, ["sign", "verify"]);
const pkcs8 = await crypto.subtle.exportKey("pkcs8", keyPair.privateKey);
const pem = "-----BEGIN PRIVATE KEY-----\n" +
  btoa(String.fromCharCode(...new Uint8Array(pkcs8))).replace(/(.{64})/g, "$1\n") +
  "\n-----END PRIVATE KEY-----\n";
Deno.env.set("FCM_SERVICE_ACCOUNT", JSON.stringify({
  type: "service_account", project_id: "demo-proj", client_email: "push@demo-proj.iam.gserviceaccount.com",
  private_key: pem, token_uri: "https://oauth2.googleapis.com/token",
}));

// --- Mock network ------------------------------------------------------------
const fcmCalls: any[] = [];
const deleted: string[] = [];
let oauthCalls = 0;
const tokens = [
  { token: "good", platform: "android" },
  { token: "dead404", platform: "android" },
  { token: "unreg", platform: "ios" },
  { token: "badtoken", platform: "web" },
  { token: "servererr", platform: "android" },
];

globalThis.fetch = async (input: RequestInfo | URL, init?: RequestInit) => {
  const req = new Request(input, init);
  const url = new URL(req.url);
  const respond = (body: unknown, status = 200) =>
    new Response(body === null ? null : JSON.stringify(body), { status, headers: { "Content-Type": "application/json" } });

  if (url.host === "oauth2.googleapis.com") {
    oauthCalls++;
    const form = new URLSearchParams(await req.text());
    const [h, p, s] = form.get("assertion")!.split(".");
    const ok = await crypto.subtle.verify("RSASSA-PKCS1-v1_5", keyPair.publicKey, b64urlDecode(s),
      new TextEncoder().encode(`${h}.${p}`));
    const header = JSON.parse(new TextDecoder().decode(b64urlDecode(h)));
    const claims = JSON.parse(new TextDecoder().decode(b64urlDecode(p)));
    assert(ok, "JWT signature verifies with the service-account public key");
    assert(header.alg === "RS256" && form.get("grant_type") === "urn:ietf:params:oauth:grant-type:jwt-bearer", "JWT header + grant type");
    assert(claims.scope === "https://www.googleapis.com/auth/firebase.messaging" &&
      claims.iss === "push@demo-proj.iam.gserviceaccount.com" && claims.aud === "https://oauth2.googleapis.com/token" &&
      claims.exp - claims.iat === 3600, "JWT claims");
    return respond({ access_token: "ya29.mock", expires_in: 3599, token_type: "Bearer" });
  }

  if (url.host === "fcm.googleapis.com") {
    assert(url.pathname === "/v1/projects/demo-proj/messages:send", "FCM endpoint uses project_id");
    assert(req.headers.get("Authorization") === "Bearer ya29.mock", "FCM call uses access token");
    const body = await req.json();
    fcmCalls.push(body);
    switch (body.message.token) {
      case "good": return respond({ name: "projects/demo-proj/messages/1" });
      case "dead404": return respond({ error: { code: 404, status: "NOT_FOUND", message: "Requested entity was not found." } }, 404);
      case "unreg": return respond({ error: { code: 400, status: "INVALID_ARGUMENT", message: "x",
        details: [{ "@type": "type.googleapis.com/google.firebase.fcm.v1.FcmError", errorCode: "UNREGISTERED" }] } }, 400);
      case "badtoken": return respond({ error: { code: 400, status: "INVALID_ARGUMENT",
        message: "The registration token is not a valid FCM registration token",
        details: [{ "@type": "type.googleapis.com/google.rpc.BadRequest",
          fieldViolations: [{ field: "message.token", description: "Invalid registration token" }] }] } }, 400);
      default: return respond({ error: { code: 500, status: "INTERNAL", message: "boom" } }, 500);
    }
  }

  if (url.host === "supabase.test") {
    assert(req.headers.get("apikey") === "srk", "Supabase calls use the service role key");
    if (url.pathname === "/rest/v1/notifications") {
      const row = { id: 42, player_id: "p1", title: "🏆 You won", body: "+36 coins",
        data: { type: "duel_result", pool_id: "abc", rank: 1, prize: 36 } };
      const wantsObject = (req.headers.get("Accept") ?? "").includes("vnd.pgrst.object");
      return respond(url.searchParams.get("id") === "eq.42" ? (wantsObject ? row : [row]) : (wantsObject ? null : []));
    }
    if (url.pathname === "/rest/v1/push_tokens" && req.method === "GET") return respond(tokens);
    if (url.pathname === "/rest/v1/push_tokens" && req.method === "DELETE") {
      const m = url.searchParams.get("token")!.match(/^in\.\((.*)\)$/)!;
      deleted.push(...m[1].split(",").map((t) => t.replace(/"/g, "")));
      return new Response(null, { status: 204 });
    }
  }
  throw new Error("unexpected fetch " + req.method + " " + req.url);
};

// --- Load the function, capturing its handler instead of starting a server ---
let handler!: (req: Request) => Promise<Response>;
(Deno as any).serve = (h: any) => { handler = h; return {} as any; };
await import(new URL("../functions/send-push/index.ts", import.meta.url).href);

const call = (body: unknown, key = "srk") => handler(new Request("http://fn/send-push", {
  method: "POST", headers: { Authorization: `Bearer ${key}`, "Content-Type": "application/json" },
  body: JSON.stringify(body),
}));

// --- Tests --------------------------------------------------------------------
let res = await call({ notification_id: 42 }, "wrong");
assert(res.status === 401, "wrong key -> 401");
res = await call({ nope: 1 });
assert(res.status === 400, "bad body -> 400");

res = await call({ notification_id: 42 });
const summary = await res.json();
console.log("   summary:", JSON.stringify(summary));
assert(res.status === 200, "send -> 200");
assert(summary.devices === 5 && summary.sent === 1 && summary.failed === 4 && summary.removed === 3, "summary counts");
assert(deleted.sort().join(",") === "badtoken,dead404,unreg", "dead tokens deleted, server error kept");
const msg = fcmCalls.find((c) => c.message.token === "good").message;
assert(msg.notification.title === "🏆 You won" && msg.notification.body === "+36 coins", "notification title/body");
assert(JSON.stringify(msg.data) === JSON.stringify({ type: "duel_result", pool_id: "abc", rank: "1", prize: "36", notification_id: "42" }),
  "data is a string map");
assert(msg.android.priority === "HIGH" && msg.apns.payload.aps.sound === "default", "android priority + apns sound");

res = await call({ notification_id: 42 });
assert(oauthCalls === 1, "access token cached between calls");

res = await call({ notification_id: 7 });
assert(res.status === 404, "unknown notification -> 404");
console.log("SEND-PUSH TESTS PASSED");
