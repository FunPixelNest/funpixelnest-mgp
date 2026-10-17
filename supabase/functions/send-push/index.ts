// =============================================================================
// send-push — Supabase Edge Function (Deno)
// =============================================================================
// Called by the database trigger on public.notifications (see migration
// 20261017000003_push.sql) with:   POST { "notification_id": 123 }
//                                   Authorization: Bearer <service role key>
//
// What it does:
//   1. Checks the caller really is our database (service role key).
//   2. Loads the notification and every push token of that player.
//   3. Gets a Google OAuth2 access token from the Firebase service account
//      (secret FCM_SERVICE_ACCOUNT), cached in memory until shortly before expiry.
//   4. Sends one FCM HTTP v1 message per device token.
//   5. Deletes tokens Firebase says are dead (app uninstalled, token rotated…).
//   6. Answers with a small JSON summary (visible in net._http_response / logs).
//
// Secrets it needs (Dashboard → Edge Functions → Secrets, or `supabase secrets set`):
//   FCM_SERVICE_ACCOUNT   the whole Firebase service-account JSON file, as one string
//   (SUPABASE_URL and SUPABASE_SERVICE_ROLE_KEY are provided automatically.)
// Optional:
//   PUSH_FUNCTION_SECRET  an extra accepted bearer value, if you store this in the
//                         vault as "service_role_key" instead of the real key.
//
// Deploy with:  supabase functions deploy send-push --no-verify-jwt
// (We check the key ourselves below, so the gateway JWT check is not needed.)
// =============================================================================

import { createClient } from "npm:@supabase/supabase-js@2";

const SUPABASE_URL = Deno.env.get("SUPABASE_URL") ?? "";
const SERVICE_ROLE_KEY = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? "";
const EXTRA_SECRET = Deno.env.get("PUSH_FUNCTION_SECRET") ?? "";

const FCM_SCOPE = "https://www.googleapis.com/auth/firebase.messaging";
const DEFAULT_TOKEN_URI = "https://oauth2.googleapis.com/token";

interface ServiceAccount {
  project_id: string;
  client_email: string;
  private_key: string;
  token_uri?: string;
}

// ---------------------------------------------------------------------------
// Small helpers
// ---------------------------------------------------------------------------

function json(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}

/** Constant-time string comparison (avoids leaking the key through timing). */
function safeEqual(a: string, b: string): boolean {
  if (!a || !b || a.length !== b.length) return false;
  let diff = 0;
  for (let i = 0; i < a.length; i++) diff |= a.charCodeAt(i) ^ b.charCodeAt(i);
  return diff === 0;
}

/** base64url without padding, as JWTs require. */
function base64url(input: ArrayBuffer | Uint8Array | string): string {
  const bytes = typeof input === "string"
    ? new TextEncoder().encode(input)
    : input instanceof Uint8Array ? input : new Uint8Array(input);
  let bin = "";
  for (const b of bytes) bin += String.fromCharCode(b);
  return btoa(bin).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}

/** Turns the PEM private key from the service account into a Web Crypto key. */
async function importPrivateKey(pem: string): Promise<CryptoKey> {
  const b64 = pem
    .replace(/-----BEGIN PRIVATE KEY-----/, "")
    .replace(/-----END PRIVATE KEY-----/, "")
    .replace(/\\n/g, "") // in case the newlines arrived escaped
    .replace(/\s+/g, "");
  const der = Uint8Array.from(atob(b64), (c) => c.charCodeAt(0));
  return await crypto.subtle.importKey(
    "pkcs8",
    der,
    { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" },
    false,
    ["sign"],
  );
}

// ---------------------------------------------------------------------------
// Google OAuth2 access token (cached in module scope between requests)
// ---------------------------------------------------------------------------

let serviceAccount: ServiceAccount | null = null;
let cachedToken: { value: string; expiresAt: number } | null = null;

function getServiceAccount(): ServiceAccount {
  if (serviceAccount) return serviceAccount;
  const raw = Deno.env.get("FCM_SERVICE_ACCOUNT");
  if (!raw) throw new Error("FCM_SERVICE_ACCOUNT secret is not set");
  const parsed = JSON.parse(raw) as ServiceAccount;
  if (!parsed.project_id || !parsed.client_email || !parsed.private_key) {
    throw new Error("FCM_SERVICE_ACCOUNT is missing project_id, client_email or private_key");
  }
  serviceAccount = parsed;
  return parsed;
}

async function getAccessToken(): Promise<string> {
  const now = Math.floor(Date.now() / 1000);
  // Reuse the token until 5 minutes before it expires.
  if (cachedToken && cachedToken.expiresAt - 300 > now) return cachedToken.value;

  const sa = getServiceAccount();
  const tokenUri = sa.token_uri || DEFAULT_TOKEN_URI;

  // Signed JWT ("I am this service account, I want the FCM scope").
  const header = { alg: "RS256", typ: "JWT" };
  const claims = { iss: sa.client_email, scope: FCM_SCOPE, aud: tokenUri, iat: now, exp: now + 3600 };
  const unsigned = `${base64url(JSON.stringify(header))}.${base64url(JSON.stringify(claims))}`;
  const key = await importPrivateKey(sa.private_key);
  const signature = await crypto.subtle.sign("RSASSA-PKCS1-v1_5", key, new TextEncoder().encode(unsigned));
  const assertion = `${unsigned}.${base64url(signature)}`;

  // Exchange the JWT for an access token.
  const res = await fetch(tokenUri, {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({
      grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer",
      assertion,
    }),
  });
  if (!res.ok) throw new Error(`OAuth token exchange failed: ${res.status} ${await res.text()}`);
  const data = await res.json() as { access_token: string; expires_in?: number };
  cachedToken = { value: data.access_token, expiresAt: now + (data.expires_in ?? 3600) };
  return data.access_token;
}

// ---------------------------------------------------------------------------
// FCM
// ---------------------------------------------------------------------------

/** FCM "data" must be a map of strings: stringify everything that is not a string. */
function toStringMap(data: Record<string, unknown> | null | undefined): Record<string, string> {
  const out: Record<string, string> = {};
  for (const [k, v] of Object.entries(data ?? {})) {
    if (v === null || v === undefined) continue;
    out[k] = typeof v === "string" ? v : JSON.stringify(v);
  }
  return out;
}

/** True when FCM's error means "this device token is dead — delete it". */
function isDeadTokenError(status: number, body: any): boolean {
  if (status === 404) return true; // NOT_FOUND: token no longer registered
  const err = body?.error ?? {};
  const details: any[] = Array.isArray(err.details) ? err.details : [];
  if (details.some((d) => d?.errorCode === "UNREGISTERED")) return true;
  if (err.status === "INVALID_ARGUMENT") {
    // Only when the complaint is about the token itself, not our payload.
    const aboutToken = details.some((d) =>
      Array.isArray(d?.fieldViolations) &&
      d.fieldViolations.some((f: any) => String(f?.field ?? "").includes("token"))
    ) || /registration token|message\.token/i.test(String(err.message ?? ""));
    return aboutToken;
  }
  return false;
}

// ---------------------------------------------------------------------------
// Request handler
// ---------------------------------------------------------------------------

Deno.serve(async (req) => {
  if (req.method !== "POST") return json({ error: "method_not_allowed" }, 405);

  // 1) Only our database (holding the service role key) may call this.
  const auth = req.headers.get("Authorization") ?? "";
  const bearer = auth.startsWith("Bearer ") ? auth.slice(7).trim() : "";
  if (!safeEqual(bearer, SERVICE_ROLE_KEY) && !safeEqual(bearer, EXTRA_SECRET)) {
    return json({ error: "unauthorized" }, 401);
  }

  let notificationId: number;
  try {
    const body = await req.json();
    notificationId = Number(body?.notification_id);
    if (!Number.isFinite(notificationId)) throw new Error();
  } catch {
    return json({ error: "body must be {\"notification_id\": <id>}" }, 400);
  }

  // 2) Load the notification and the player's devices (service role bypasses RLS).
  const db = createClient(SUPABASE_URL, SERVICE_ROLE_KEY, {
    auth: { persistSession: false, autoRefreshToken: false },
  });

  const { data: notification, error: nErr } = await db
    .from("notifications")
    .select("id, player_id, title, body, data")
    .eq("id", notificationId)
    .maybeSingle();
  if (nErr) return json({ error: nErr.message }, 500);
  if (!notification) return json({ error: "notification_not_found" }, 404);

  const { data: tokens, error: tErr } = await db
    .from("push_tokens")
    .select("token, platform")
    .eq("player_id", notification.player_id);
  if (tErr) return json({ error: tErr.message }, 500);
  if (!tokens || tokens.length === 0) {
    return json({ notification_id: notificationId, devices: 0, sent: 0, failed: 0, removed: 0 });
  }

  // 3) OAuth token for FCM.
  let accessToken: string;
  let projectId: string;
  try {
    projectId = getServiceAccount().project_id;
    accessToken = await getAccessToken();
  } catch (e) {
    console.error("FCM auth error:", e);
    return json({ error: String(e) }, 500);
  }

  // 4) One message per device.
  const data = toStringMap({ ...(notification.data ?? {}), notification_id: notification.id });
  const endpoint = `https://fcm.googleapis.com/v1/projects/${projectId}/messages:send`;
  const deadTokens: string[] = [];
  const errors: string[] = [];
  let sent = 0;

  await Promise.all(tokens.map(async ({ token }) => {
    const message = {
      message: {
        token,
        notification: { title: notification.title, body: notification.body },
        data,
        android: { priority: "HIGH" },
        apns: { payload: { aps: { sound: "default" } } },
      },
    };
    try {
      const res = await fetch(endpoint, {
        method: "POST",
        headers: { "Authorization": `Bearer ${accessToken}`, "Content-Type": "application/json" },
        body: JSON.stringify(message),
      });
      if (res.ok) {
        sent++;
        return;
      }
      const errBody = await res.json().catch(() => ({}));
      if (isDeadTokenError(res.status, errBody)) {
        deadTokens.push(token);
      } else {
        errors.push(`${res.status} ${errBody?.error?.status ?? ""} ${errBody?.error?.message ?? ""}`.trim());
      }
      // A 401 means our cached access token went bad: drop it so the next call refreshes.
      if (res.status === 401) cachedToken = null;
    } catch (e) {
      errors.push(String(e));
    }
  }));

  // 5) Forget devices that will never receive pushes again.
  if (deadTokens.length > 0) {
    const { error: dErr } = await db.from("push_tokens").delete().in("token", deadTokens);
    if (dErr) errors.push(`token cleanup failed: ${dErr.message}`);
  }

  if (errors.length > 0) console.error("send-push errors:", errors);

  // 6) Summary.
  return json({
    notification_id: notificationId,
    devices: tokens.length,
    sent,
    failed: tokens.length - sent,
    removed: deadTokens.length,
    errors,
  });
});
