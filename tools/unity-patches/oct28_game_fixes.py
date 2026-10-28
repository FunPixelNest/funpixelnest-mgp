#!/usr/bin/env python3
"""Game fixes of 28 Oct 2026, applied to the Unity project (FunPixelNestGames/Assets).

  python3 oct28_game_fixes.py <path to Assets>

Idempotent: every edit checks whether it is already in place. Fails loudly when an anchor is missing.
  - Mahjong: keep the countdown running in the app (only hide the Timer's visuals), so the HUD shows 2:00 and the
    3-2-1-GO countdown unlocks the board.
  - Kitty Hole: 2-minute round.
  - Blackjack: 3 lives.
  - Snake War: power-up buttons sit lower (closer to the bottom edge).
  - Bird Dash (FlapDash): music, flap / coin / hit sounds following the app's settings; no power-up pickups.
  - Queen Sudoku: puzzle picked from both 6x6 catalogs (11 puzzles instead of 4).
"""
import os
import sys

ASSETS = sys.argv[1]
changed = []


def edit(rel, old, new, done_marker):
    path = os.path.join(ASSETS, rel)
    s = open(path, encoding='utf-8').read()
    if done_marker in s:
        return
    if s.count(old) != 1:
        sys.exit(f'anchor not found once in {rel}: {old[:70]!r} (found {s.count(old)})')
    s = s.replace(old, new)
    open(path, 'w', encoding='utf-8').write(s)
    changed.append(rel)


# ---------------------------------------------------------------- Mahjong: timer keeps running
edit('Mahjong/Scripts/FunPixelNest/FpnMahjongLink.cs',
     '''                foreach (string name in new[] { "Timer", "ScoreCounter", "PauseButton", "ButtonToMap", "ButtonMenu", "Level", "PossibleMatchesCounter" })
                {
                    Transform child = header.transform.Find(name);
                    if (child != null)
                    {
                        child.gameObject.SetActive(false);
                    }
                }''',
     '''                foreach (string name in new[] { "ScoreCounter", "PauseButton", "ButtonToMap", "ButtonMenu", "Level", "PossibleMatchesCounter" })
                {
                    Transform child = header.transform.Find(name);
                    if (child != null)
                    {
                        child.gameObject.SetActive(false);
                    }
                }

                // The Timer object carries the round clock (LevelCountdownTimer: 2:00, 3-2-1-GO, time up), so it
                // must stay active; only its visuals are hidden (the app's HUD shows the time).
                Transform timerUi = header.transform.Find("Timer");
                if (timerUi != null)
                {
                    CanvasGroup hide = timerUi.GetComponent<CanvasGroup>();
                    if (hide == null) hide = timerUi.gameObject.AddComponent<CanvasGroup>();
                    hide.alpha = 0f;
                    hide.interactable = false;
                    hide.blocksRaycasts = false;
                }''',
     'The Timer object carries the round clock')

edit('Mahjong/Scripts/FunPixelNest/FpnMahjongLink.cs',
     '            timer = FindObjectOfType<LevelCountdownTimer>();',
     '            timer = FindObjectOfType<LevelCountdownTimer>(true);',
     'FindObjectOfType<LevelCountdownTimer>(true)')

# ---------------------------------------------------------------- Kitty Hole: 2 minutes
edit('KittyHole/Script/FunPixelNest/FpnKittyHoleLink.cs',
     '        public const int RoundSeconds = 180;',
     '        public const int RoundSeconds = 120;',
     'RoundSeconds = 120;')

# ---------------------------------------------------------------- Blackjack: 3 lives (scene value overrode the default 3)
edit('Blackjack/Scenes/Blackjack.unity',
     '  startingLives: 10\n',
     '  startingLives: 3\n',
     '  startingLives: 3\n')

# ---------------------------------------------------------------- Snake War: power-ups lower
edit('SnakeWar/Classes/FunPixelNest/FpnSnakeWarLink.cs',
     '''                else if (child.anchorMax.y < 0.01f)
                {
                    child.anchoredPosition += new Vector2(0f, bottom);
                }
            }
        }''',
     '''                else if (child.anchorMax.y < 0.01f)
                {
                    child.anchoredPosition += new Vector2(0f, bottom);
                }
            }

            // Power-up buttons: lower, with their bottom edge just above the app's bottom inset.
            RectTransform boosters = mainCanvas.transform.Find("BoostersPanel") as RectTransform;
            if (boosters != null)
            {
                const float gapFromBottom = 30f;
                float y = bottom + gapFromBottom + boosters.rect.height * boosters.pivot.y;
                if (y < boosters.anchoredPosition.y)
                {
                    boosters.anchoredPosition = new Vector2(boosters.anchoredPosition.x, y);
                }
            }
        }''',
     'Power-up buttons: lower')

# ---------------------------------------------------------------- Bird Dash: no power-up pickups
edit('FlapDash/Scripts/Spawner.cs',
     '''    bool TrySpawnPowerup(Pipes pipes)
    {
''',
     '''    bool TrySpawnPowerup(Pipes pipes)
    {
        // Bird Dash: power-ups are off (no lightning pickups, no power-up cards); coins spawn in their place.
        if (powerupsEnabled == false) return false;

''',
     'powerupsEnabled == false')
edit('FlapDash/Scripts/Spawner.cs',
     '    [SerializeField] private GameObject powerupPickupPrefab;',
     '    [SerializeField] private GameObject powerupPickupPrefab;\n    public static bool powerupsEnabled = false;',
     'public static bool powerupsEnabled')

# ---------------------------------------------------------------- Bird Dash: sounds
SOUND_MANAGER = '''using UnityEngine;

/// <summary>
/// Bird Dash sounds and vibration. Music loop plus flap, coin and hit sounds (Resources/FlapDashAudio),
/// following the FunPixelNest app's Music / Sound / Vibration settings inside the app.
/// </summary>
public class SoundManager : MonoBehaviour
{
    public static SoundManager Instance { get; private set; }

    [Header("Settings")]
    public bool isVibrationEnabled = true;
    public bool isMusicEnabled = true;
    public bool isSoundEnabled = true;

    [Header("Volumes")]
    [Range(0f, 1f)] public float musicVolume = 0.45f;
    [Range(0f, 1f)] public float sfxVolume = 0.8f;

    AudioSource musicSource;
    AudioSource sfxSource;
    AudioClip musicClip, flapClip, coinClip, hitClip;
    float lastFlapAt = -1f;

    private void Awake()
    {
        if (Instance == null)
        {
            Instance = this;
            // Persists across the game's own scenes; inside the FunPixelNest app it lives with the game scene.
            if (!FunPixelNest.FlapDash.FpnFlapDashLink.InApp)
                DontDestroyOnLoad(gameObject);
            SetUpAudio();
        }
        else
        {
            Destroy(gameObject);
        }
    }

    private void Start()
    {
        RefreshMusic();
    }

    private void OnDestroy()
    {
        if (Instance == this) Instance = null;
    }

    void SetUpAudio()
    {
        musicClip = Resources.Load<AudioClip>("FlapDashAudio/bd_music");
        flapClip = Resources.Load<AudioClip>("FlapDashAudio/bd_flap");
        coinClip = Resources.Load<AudioClip>("FlapDashAudio/bd_coin");
        hitClip = Resources.Load<AudioClip>("FlapDashAudio/bd_hit");

        musicSource = gameObject.AddComponent<AudioSource>();
        musicSource.playOnAwake = false;
        musicSource.loop = true;
        musicSource.clip = musicClip;
        musicSource.volume = musicVolume;

        sfxSource = gameObject.AddComponent<AudioSource>();
        sfxSource.playOnAwake = false;
        sfxSource.loop = false;
        sfxSource.volume = sfxVolume;
    }

    /// <summary>Applies the Music and Sound switches (inside the app they come from the app's settings).</summary>
    public void SetAudioEnabled(bool music, bool sound)
    {
        isMusicEnabled = music;
        isSoundEnabled = sound;
        RefreshMusic();
    }

    void RefreshMusic()
    {
        if (musicSource == null) return;
        musicSource.volume = musicVolume;
        if (isMusicEnabled && musicClip != null)
        {
            if (!musicSource.isPlaying) musicSource.Play();
        }
        else if (musicSource.isPlaying)
        {
            musicSource.Stop();
        }
    }

    void PlaySfx(AudioClip clip, float volume = 1f, float pitchJitter = 0f)
    {
        if (!isSoundEnabled || clip == null || sfxSource == null) return;
        sfxSource.pitch = pitchJitter > 0f ? 1f + Random.Range(-pitchJitter, pitchJitter) : 1f;
        sfxSource.PlayOneShot(clip, volume);
    }

    // Call this from your UI Buttons (via Inspector or code)
    public void PlayButtonClickSound()
    {
        PlayVibration();
    }
    public void ButtonClickSound()
    {
        PlayVibrationLow();
    }

    // Call this when the player hits a pipe or the ground
    public void PlayCollisionSound()
    {
        PlaySfx(hitClip, 1f);
        PlayVibration();
    }

    // Call this when the player scores a point (optional)
    public void PlayScoreSound()
    {
    }

    /// <summary>A coin was collected.</summary>
    public void PlayCoinSound()
    {
        PlaySfx(coinClip, 0.75f);
    }

    public void PlayPowerupPickupSound()
    {
        PlayVibrationLow();
    }

    public void PlayPowerupSelectSound()
    {
        PlayVibrationLow();
    }

    /// <summary>The bird flapped (one tap).</summary>
    public void PlayTapSound()
    {
        // Very fast tapping: skip flaps closer than 50 ms so the sound doesn't stack up.
        if (Time.unscaledTime - lastFlapAt >= 0.05f)
        {
            lastFlapAt = Time.unscaledTime;
            PlaySfx(flapClip, 0.55f, 0.05f);
        }
        PlayVibrationLow();
    }

    private void PlayVibration()
    {
        if (!isVibrationEnabled) return;

        // FunPixelNest games build: no MOST haptics plugin, a short system vibration on hits instead.
#if UNITY_ANDROID || UNITY_IOS
        Handheld.Vibrate();
#endif
    }
    private void PlayVibrationLow()
    {
        if (!isVibrationEnabled) return;

        // Light taps: no vibration in the FunPixelNest games build (system vibration is too strong per flap).
    }
}
'''
sm_path = os.path.join(ASSETS, 'FlapDash/Scripts/SoundManager.cs')
if 'Resources/FlapDashAudio' not in open(sm_path, encoding='utf-8').read():
    open(sm_path, 'w', encoding='utf-8').write(SOUND_MANAGER)
    changed.append('FlapDash/Scripts/SoundManager.cs')

edit('FlapDash/Scripts/Coin.cs',
     '''        if (other.CompareTag("Player"))
        {
            GameManager.Instance.AddCoin();''',
     '''        if (other.CompareTag("Player"))
        {
            if (SoundManager.Instance != null) SoundManager.Instance.PlayCoinSound();
            GameManager.Instance.AddCoin();''',
     '            if (SoundManager.Instance != null) SoundManager.Instance.PlayCoinSound();\n            GameManager.Instance.AddCoin();')
edit('FlapDash/Scripts/Coin.cs',
     '''    void CollectCoin()
    {
        GameManager.Instance.AddCoin();''',
     '''    void CollectCoin()
    {
        if (SoundManager.Instance != null) SoundManager.Instance.PlayCoinSound();
        GameManager.Instance.AddCoin();''',
     'void CollectCoin()\n    {\n        if (SoundManager.Instance')

edit('FlapDash/Scripts/FunPixelNest/FpnFlapDashLink.cs',
     '''                SoundManager.Instance.isVibrationEnabled = settings.vibration;''',
     '''                SoundManager.Instance.isVibrationEnabled = settings.vibration;
                SoundManager.Instance.SetAudioEnabled(settings.music, settings.sound);''',
     'SetAudioEnabled(settings.music, settings.sound)')
edit('FlapDash/Scripts/FunPixelNest/FpnFlapDashLink.cs',
     '    /// - Vibration follows the app\'s settings (the game has no music or sound effects).',
     '    /// - Music, sounds and vibration follow the app\'s settings. Power-up pickups are off.',
     'Music, sounds and vibration follow')

# ---------------------------------------------------------------- Queen Sudoku: 11 puzzles in rotation
edit('QueenSudoku/Scripts/FunPixelNest/FpnQueenSudokuLink.cs',
     '''            GameManager.PrepareSkillzMatch(new SkillzSDK.Match());
        }''',
     '''            // One pool from both 6x6 catalogs (classic + new levels): the classic list alone has only 4 puzzles,
            // so the same board kept coming back. The pick still comes from the match seed (same board for
            // everyone in a pool).
            int classic = LevelCatalog.GetPlayableCount(MatchGridSize);
            int newer = LevelCatalog.GetNewLevelCount(MatchGridSize);
            LevelAssetRef pick = null;
            if (classic + newer > 0)
            {
                int index = NextInt(0, classic + newer);
                pick = index < classic
                    ? LevelCatalog.GetRandomByIndex(MatchGridSize, index)
                    : LevelCatalog.GetRandomNewLevelByIndex(MatchGridSize, index - classic);
            }
            if (pick != null && pick.IsValid && !LevelCatalog.IsTutorialPuzzle(pick.puzzleId))
            {
                GameManager.SelectLevelAsset(pick);
                Debug.Log("[QueenSudoku] match puzzle " + pick.puzzleId + " (" + (classic + newer) + " in rotation)");
            }
            else
            {
                GameManager.PrepareSkillzMatch(new SkillzSDK.Match());
            }
        }''',
     'One pool from both 6x6 catalogs')

print('changed:', *sorted(set(changed)) or ['nothing (already applied)'], sep='\n  ')
