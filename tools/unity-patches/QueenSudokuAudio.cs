using FunPixelNest;
using UnityEngine;

/// <summary>
/// Queen Sudoku music and sound effects (clips in Resources/QueenSudokuAudio, made by tools/sound/cardpuzzle_synth.py).
/// Created on first use and lives with the game scene. Inside the FunPixelNest app it follows the app's Music and
/// Sound settings; the app's pause menu pauses audio through the SDK.
/// </summary>
public class QueenSudokuAudio : MonoBehaviour
{
    const string Folder = "QueenSudokuAudio/";
    const float MusicVolume = 0.4f;
    const float SfxVolume = 0.85f;

    static QueenSudokuAudio s_instance;

    AudioSource music;
    AudioSource sfx;
    AudioClip musicClip, mark, unmark, remove, conflict, life, hint, win, timeUp;
    AudioClip[] queens;
    bool musicOn = true;
    bool soundOn = true;
    float lastTickAt = -1f;
    float lastLifeAt = -1f;

    static QueenSudokuAudio Instance
    {
        get
        {
            if (s_instance == null)
                s_instance = new GameObject("QueenSudokuAudio").AddComponent<QueenSudokuAudio>();
            return s_instance;
        }
    }

    void Awake()
    {
        musicClip = Load("qs_music");
        mark = Load("qs_mark");
        unmark = Load("qs_unmark");
        remove = Load("qs_remove");
        conflict = Load("qs_conflict");
        life = Load("qs_life");
        hint = Load("qs_hint");
        win = Load("qs_win");
        timeUp = Load("qs_timeup");
        queens = new AudioClip[6];
        for (int i = 0; i < queens.Length; i++) queens[i] = Load("qs_queen_" + (i + 1));

        music = gameObject.AddComponent<AudioSource>();
        music.playOnAwake = false;
        music.loop = true;
        music.volume = MusicVolume;
        music.clip = musicClip;
        sfx = gameObject.AddComponent<AudioSource>();
        sfx.playOnAwake = false;
        sfx.volume = SfxVolume;

        if (FpnPlatform.IsHosted)
        {
            ApplySettings(FpnSession.Settings);
            FpnSession.OnSettingsChanged += ApplySettings;
        }
    }

    void OnDestroy()
    {
        FpnSession.OnSettingsChanged -= ApplySettings;
        if (s_instance == this) s_instance = null;
    }

    static AudioClip Load(string name) => Resources.Load<AudioClip>(Folder + name);

    void ApplySettings(FpnSettings settings)
    {
        if (settings == null) return;
        musicOn = settings.music;
        soundOn = settings.sound;
        RefreshMusic();
    }

    void RefreshMusic()
    {
        if (music == null) return;
        if (musicOn && musicClip != null) { if (!music.isPlaying) music.Play(); }
        else if (music.isPlaying) music.Stop();
    }

    void Play(AudioClip clip, float volume = 1f)
    {
        if (!soundOn || clip == null || sfx == null) return;
        sfx.PlayOneShot(clip, volume);
    }

    /// <summary>Starts the background music (call when the board starts).</summary>
    public static void StartMusic() => Instance.RefreshMusic();

    /// <summary>X mark added (taps and drags; rapid drag ticks are thinned out).</summary>
    public static void Mark() => Instance.Tick(Instance.mark);

    /// <summary>X mark removed.</summary>
    public static void Unmark() => Instance.Tick(Instance.unmark);

    void Tick(AudioClip clip)
    {
        if (Time.unscaledTime - lastTickAt < 0.045f) return;
        lastTickAt = Time.unscaledTime;
        Play(clip, 0.8f);
    }

    /// <summary>A queen was placed; <paramref name="count"/> queens on the board now (pitch climbs with it).</summary>
    public static void QueenPlaced(int count)
    {
        var a = Instance;
        int i = Mathf.Clamp(count, 1, a.queens.Length) - 1;
        a.Play(a.queens[i]);
    }

    public static void QueenRemoved() => Instance.Play(Instance.remove, 0.8f);

    /// <summary>Queen can't go there (skipped right after the life-lost sound, which covers it).</summary>
    public static void Conflict()
    {
        var a = Instance;
        if (Time.unscaledTime - a.lastLifeAt < 0.15f) return;
        a.Play(a.conflict);
    }

    public static void LifeLost()
    {
        var a = Instance;
        a.lastLifeAt = Time.unscaledTime;
        a.Play(a.life);
    }

    public static void Hint() => Instance.Play(Instance.hint);

    public static void Win()
    {
        var a = Instance;
        if (a.music != null) a.music.volume = MusicVolume * 0.4f;
        a.Play(a.win);
    }

    public static void TimeUp()
    {
        var a = Instance;
        if (a.music != null) a.music.volume = MusicVolume * 0.4f;
        a.Play(a.timeUp);
    }
}
