using System.Collections.Generic;
using FunPixelNest;
using UnityEngine;

/// <summary>
/// Minimal FunPixelNest integration. Drop on any GameObject in an empty scene and press Play.
/// Uses IMGUI so it has no package dependencies.
///
///  1. Read launch data          -> FpnPlatform.LaunchData
///  2. Seed the deck             -> FpnRandom.FromLaunch().Shuffle(deck)
///  3. Start the clock           -> FpnPlatform.StartMatch()
///  4. Game over: submit once    -> FpnPlatform.SubmitScore(score)   (or FpnPlatform.Quit())
/// </summary>
public class FpnQuickStartExample : MonoBehaviour
{
    [Tooltip("Editor only: fixed seed for reproducible testing. 0 = random.")]
    public long editorTestSeed = 0;

    private readonly List<int> _deck = new List<int>(52);
    private int _nextCard;
    private long _score;
    private string _status = "";
    private bool _finished;

    private void Awake()
    {
        if (!FpnPlatform.IsHosted && editorTestSeed != 0)
            FpnPlatform.SetTestLaunchData(editorTestSeed, FpnMode.Practice, "quickstart");

        FpnPlatform.OnScoreSubmitted += HandleScoreSubmitted;
    }

    private void OnDestroy()
    {
        FpnPlatform.OnScoreSubmitted -= HandleScoreSubmitted;
    }

    private void Start()
    {
        // 1. Launch data
        FpnLaunchData data = FpnPlatform.LaunchData;

        // 2. Build the deck from the match seed: every player in this match gets the same order.
        FpnRandom rng = FpnRandom.FromLaunch();
        _deck.Clear();
        for (int i = 0; i < 52; i++) _deck.Add(i);
        rng.Shuffle(_deck);

        // 3. Play begins now.
        FpnPlatform.StartMatch();

        _status = "Hi " + data.DisplayName + " - " + data.Mode + " (seed " + data.Seed + ")";
    }

    private void DrawCard()
    {
        if (_finished || _nextCard >= _deck.Count) return;
        int card = _deck[_nextCard++];
        FpnPlatform.AddEvent("d:" + card);  // lightweight move log, sent with the score

        // Toy scoring: score depends only on the (seeded) cards and the player's choices.
        int rank = card % 13 + 1;
        _score += rank;

        if (_nextCard >= 10) GameOver();
    }

    private void GameOver()
    {
        if (_finished) return;
        _finished = true;
        // 4. Exactly once.
        FpnPlatform.SubmitScore(_score);
    }

    private void HandleScoreSubmitted(long score)
    {
        _status = "Final score " + score + ". Waiting for platform...";
    }

    private void OnGUI()
    {
        GUILayout.BeginArea(new Rect(20, 20, 420, 300));
        GUILayout.Label(_status);
        GUILayout.Label("Cards drawn: " + _nextCard + "   Score: " + _score);

        GUI.enabled = !_finished;
        if (GUILayout.Button("Draw card", GUILayout.Height(40))) DrawCard();
        if (GUILayout.Button("End game now", GUILayout.Height(40))) GameOver();
        if (GUILayout.Button("Quit (forfeit)", GUILayout.Height(40)))
        {
            _finished = true;
            FpnPlatform.Quit();
            _status = "Left the match.";
        }
        GUI.enabled = true;
        GUILayout.EndArea();
    }
}
