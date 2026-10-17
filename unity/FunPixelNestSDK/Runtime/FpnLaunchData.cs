using System;

namespace FunPixelNest
{
    /// <summary>How the current match is being played.</summary>
    public enum FpnMode
    {
        /// <summary>Free play / not hosted. Score is not prize-relevant.</summary>
        Practice = 0,
        /// <summary>Tournament: many players get the same seed; ranked on score.</summary>
        Tournament = 1,
        /// <summary>Head-to-head duel: both players get the same seed.</summary>
        Duel = 2,
    }

    /// <summary>
    /// Data the platform passes to the game before it starts. Immutable.
    /// Obtain via <see cref="FpnPlatform.LaunchData"/>.
    /// </summary>
    public sealed class FpnLaunchData
    {
        public FpnLaunchData(string gameId, FpnMode mode, long seed, string ticketId, string playerId, string displayName)
        {
            GameId = gameId ?? string.Empty;
            Mode = mode;
            Seed = seed;
            TicketId = ticketId ?? string.Empty;
            PlayerId = playerId ?? string.Empty;
            DisplayName = displayName ?? string.Empty;
        }

        /// <summary>Platform id of this game, e.g. "solitaire".</summary>
        public string GameId { get; }

        /// <summary>Practice, Tournament or Duel.</summary>
        public FpnMode Mode { get; }

        /// <summary>64-bit match seed. Every player in the same match receives the same value.
        /// Use it with <see cref="FpnRandom"/> to build the board/deck.</summary>
        public long Seed { get; }

        /// <summary>Platform ticket for this match entry (opaque).</summary>
        public string TicketId { get; }

        /// <summary>Platform player id (opaque).</summary>
        public string PlayerId { get; }

        /// <summary>Player's display name, for UI only.</summary>
        public string DisplayName { get; }

        /// <summary>True for Tournament and Duel (matches that may involve entry fees / prizes).</summary>
        public bool IsCompetitive
        {
            get { return Mode != FpnMode.Practice; }
        }

        public override string ToString()
        {
            return "FpnLaunchData(gameId=" + GameId + ", mode=" + Mode + ", seed=" + Seed
                + ", ticketId=" + TicketId + ", playerId=" + PlayerId + ", displayName=" + DisplayName + ")";
        }
    }

    /// <summary>JsonUtility DTO for window.FPN.launchData. All string fields (seed is a 64-bit string).</summary>
    [Serializable]
    internal sealed class FpnLaunchDataDto
    {
#pragma warning disable 0649 // assigned by JsonUtility
        public string gameId;
        public string mode;
        public string seed;
        public string ticketId;
        public string playerId;
        public string displayName;
#pragma warning restore 0649
    }
}
