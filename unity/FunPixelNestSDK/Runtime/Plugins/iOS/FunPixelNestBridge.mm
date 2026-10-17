// FunPixelNest SDK - iOS bridge for Unity as a Library (UaaL).
//
// STATUS: minimal skeleton, to be finished during native integration.
//
// Host app (Swift/ObjC, linking UnityFramework) usage:
//   1. Before showing Unity:     FPN_iOS_SetLaunchData("{\"gameId\":...,\"seed\":\"123\",...}");
//   2. Register result handlers: FPN_iOS_SetHandlers(&onScore, &onQuit);
//   3. Show / run Unity. The game reads the launch data via FPN_iOS_GetLaunchData (called from C#)
//      and reports the result exactly once via FPN_iOS_SubmitScore or FPN_iOS_Quit.
//
// The handler pointers are "weak" callbacks: if the host has not registered a handler yet, a submitted
// score / quit is kept (one pending result) and delivered as soon as FPN_iOS_SetHandlers is called.
//
// Threading: Unity calls these functions on its main thread (the iOS main thread in a standard
// UaaL setup). Handlers run synchronously on that thread; dispatch elsewhere if needed. The
// eventLogJson pointer passed to a handler is only valid during the call - copy it if you keep it.
//
// Symbols are exported with default visibility so the host can call them through UnityFramework
// (declare the prototypes below in a bridging header).

#include <stdlib.h>
#include <string.h>
#import <Foundation/Foundation.h>

#define FPN_EXPORT extern "C" __attribute__((visibility("default")))

typedef void (*FPN_iOS_ScoreHandler)(double score, double durationMs, const char* eventLogJson);
typedef void (*FPN_iOS_QuitHandler)(void);

static char* s_launchData = NULL;
static FPN_iOS_ScoreHandler s_scoreHandler = NULL;
static FPN_iOS_QuitHandler s_quitHandler = NULL;

// One pending result, used only if Unity reports before the host registered handlers.
static int s_pendingKind = 0; // 0 = none, 1 = score, 2 = quit
static double s_pendingScore = 0;
static double s_pendingDurationMs = 0;
static char* s_pendingEventLog = NULL;

static char* FPN_CopyString(const char* s)
{
    if (s == NULL) return NULL;
    size_t len = strlen(s) + 1;
    char* copy = (char*)malloc(len);
    if (copy != NULL) memcpy(copy, s, len);
    return copy;
}

static void FPN_FlushPending(void)
{
    if (s_pendingKind == 1 && s_scoreHandler != NULL)
    {
        s_pendingKind = 0;
        s_scoreHandler(s_pendingScore, s_pendingDurationMs, s_pendingEventLog != NULL ? s_pendingEventLog : "[]");
        free(s_pendingEventLog);
        s_pendingEventLog = NULL;
    }
    else if (s_pendingKind == 2 && s_quitHandler != NULL)
    {
        s_pendingKind = 0;
        s_quitHandler();
    }
}

// ---------------------------------------------------------------- called by the HOST app

/// Store the launch JSON (copied). Call before Unity starts / before the game scene loads.
/// Passing NULL clears it (the game will then run as "not hosted").
FPN_EXPORT void FPN_iOS_SetLaunchData(const char* launchJson)
{
    free(s_launchData);
    s_launchData = FPN_CopyString(launchJson);
}

/// Register result callbacks (either may be NULL). Delivers a pending result if one is waiting.
FPN_EXPORT void FPN_iOS_SetHandlers(FPN_iOS_ScoreHandler onScore, FPN_iOS_QuitHandler onQuit)
{
    s_scoreHandler = onScore;
    s_quitHandler = onQuit;
    FPN_FlushPending();
}

// ---------------------------------------------------------------- called by UNITY (C# DllImport)

/// Returns a malloc'd copy (or NULL). IL2CPP's string marshaler frees it with free().
FPN_EXPORT const char* FPN_iOS_GetLaunchData(void)
{
    return FPN_CopyString(s_launchData);
}

FPN_EXPORT void FPN_iOS_SubmitScore(double score, double durationMs, const char* eventLogJson)
{
    if (s_scoreHandler != NULL)
    {
        s_scoreHandler(score, durationMs, eventLogJson != NULL ? eventLogJson : "[]");
        return;
    }
    NSLog(@"[FPN] No score handler registered yet; keeping score %.0f pending.", score);
    free(s_pendingEventLog);
    s_pendingKind = 1;
    s_pendingScore = score;
    s_pendingDurationMs = durationMs;
    s_pendingEventLog = FPN_CopyString(eventLogJson);
}

FPN_EXPORT void FPN_iOS_Quit(void)
{
    if (s_quitHandler != NULL)
    {
        s_quitHandler();
        return;
    }
    NSLog(@"[FPN] No quit handler registered yet; keeping quit pending.");
    if (s_pendingKind == 0) s_pendingKind = 2;
}
