package com.doomviewer.game.objects;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class GameDefinitions {
    private Map<StateNum, StateDef> statesMap; // New map-based storage
    public Map<MobjType, MobjInfoDef> mobjInfos; // MobjType enum as key for direct access
    public Map<Integer, MobjType> doomedNumToMobjType; // For Thing.type to MobjType mapping

    // E1M1 enemy types (doomednums)
    public static final int TYPE_POSSESSED = 3004; // Zombieman
    public static final int TYPE_SHOTGUY = 9;     // Shotgun Guy
    public static final int TYPE_TROOP = 3001;    // Imp
    public static final int TYPE_SERGEANT = 3002; // Demon (Pinky)
    public static final int TYPE_SHADOWS = 58;    // Spectre
    public static final int TYPE_PLAYER_START_1 = 1; // Common doomednum for player 1 start

    public GameDefinitions() {
        statesMap = new HashMap<>(1024); // Initialize the map
        mobjInfos = new HashMap<>();
        doomedNumToMobjType = new HashMap<>();

        // Initialize with S_NULL state at index 0
        statesMap.put(StateNum.S_NULL, new StateDef(SpriteNames.TROO, 0, -1, Actions.NULL_ACTION, StateNum.S_NULL)); // S_NULL

        populatePlayer(); // Add this call
        populatePossessed(); // Zombieman
        populateShotguy();   // Shotgun Guy
        populateImp();       // Imp
        populateDemon();     // Demon/Spectre
        populateProjectiles(); // Projectiles and effects
        // Add more initializations for other mobj types and their states
    }

    private void addState(StateNum stateNum, SpriteNames sprite, int frame, int tics, MobjAction action, StateNum next) {
        statesMap.put(stateNum, new StateDef(sprite, frame, tics, action, next)); // New way
    }

    public static double bamsToDegrees(short bams) {
        // BAMS: 0x0000 East, 0x4000 North, 0x8000 West, 0xC000 South
        // Converts to 0-359.99... range, 0 is East, positive is CCW (standard math angle)
        int unsignedBams = bams & 0xFFFF;
        return unsignedBams * (360.0 / 65536.0);
    }

    private void populatePlayer() {
        // Player states (S_PLAY to S_PLAY_XDIE9)
        // For now, just a few key ones if player doesn't use full state machine like monsters
        addState(StateNum.S_PLAY, SpriteNames.PLAY, 0, -1, Actions.NULL_ACTION, StateNum.S_PLAY); // Idle
        // Add S_PLAY_RUN1 etc. if needed for player sprite animation,
        // but player movement is usually directly controlled, not by state tics.
        // Player death states can be added similarly to monsters if player character shows death anim.
        // ... (add S_PLAY_PAIN, S_PLAY_DIE1 etc. if player visuals use them)

        MobjInfoDef playerInfo = new MobjInfoDef(
                "MT_PLAYER", TYPE_PLAYER_START_1,
                StateNum.S_PLAY, 100, StateNum.S_PLAY_RUN1, // spawnstate, spawnhealth, seestate
                0, // reactiontime
                StateNum.S_PLAY_PAIN, 255, // painstate, painchance
                StateNum.S_NULL,      // meleestate (player uses weapons differently)
                StateNum.S_PLAY_ATK1, // missilestate (player uses weapons differently)
                StateNum.S_PLAY_DIE1, StateNum.S_PLAY_XDIE1, // deathstate, xdeathstate
                0, // speed (player handles its own movement speed)
                16.0, // radius (Doom default)
                56.0, // height (Doom default total height)
                100,  // mass
                0,    // damage (player deals damage via weapons)
                MobjFlags.MF_SOLID | MobjFlags.MF_SHOOTABLE | MobjFlags.MF_DROPOFF | MobjFlags.MF_PICKUP | MobjFlags.MF_NOCOUNT, // MF_NOCOUNT if player shouldn't count for kill %
                StateNum.S_NULL // raisestate
        );
        mobjInfos.put(MobjType.MT_PLAYER, playerInfo);
        doomedNumToMobjType.put(TYPE_PLAYER_START_1, MobjType.MT_PLAYER);
        // Add other player start types if your WAD uses them (e.g., for multiplayer starts)
    }

    private void populateGenericMonsterStates(MobjType mobjType, SpriteNames spriteName,
                                              StateNum stnd, StateNum stnd2,
                                              StateNum run1, StateNum runCycleEnd, // run1 to runCycleEnd (e.g. S_POSS_RUN8)
                                              StateNum atk1, StateNum atkCycleEnd,
                                              StateNum pain, StateNum painEnd,
                                              StateNum die1, StateNum dieCycleEnd,
                                              StateNum xdie1, StateNum xdieCycleEnd,
                                              StateNum raise1, StateNum raiseCycleEnd,
                                              MobjAction lookAction, MobjAction chaseAction, MobjAction attackAction,
                                              MobjAction painAction, MobjAction screamAction, MobjAction fallAction,
                                              int[] runFrames, int[] runTics,
                                              int[] atkFrames, int[] atkTics, MobjAction[] atkActions,
                                              int painFrame, int painTics,
                                              int[] dieFrames, int[] dieTics, MobjAction[] dieActions,
                                              int[] xdieFrames, int[] xdieTics, MobjAction[] xdieActions,
                                              int[] raiseFrames, int[] raiseTics
    ) {
        // Stand
        addState(stnd, spriteName, runFrames[0], 10, lookAction, stnd2); // Using runFrames[0] as base stand frame
        addState(stnd2, spriteName, runFrames[1], 10, lookAction, stnd); // Using runFrames[1] as alt stand frame

        // Run cycle
        StateNum currentRun = run1;
        for (int i = 0; i < runFrames.length; i++) {
            StateNum nextRun = (i == runFrames.length - 1) ? run1 : StateNum.values()[currentRun.ordinal() + 1];
            addState(currentRun, spriteName, runFrames[i], runTics[i], chaseAction, nextRun);
            if (currentRun == runCycleEnd) break;
            if (i < runFrames.length - 1) currentRun = nextRun;
        }

        // Attack cycle
        StateNum currentAtk = atk1;
        for (int i = 0; i < atkFrames.length; i++) {
            StateNum nextAtk = (i == atkFrames.length - 1) ? run1 : StateNum.values()[currentAtk.ordinal() + 1]; // Default to run after attack
            MobjAction currentAtkAction = (atkActions != null && i < atkActions.length && atkActions[i] != null) ? atkActions[i] : Actions::A_FaceTarget;
            addState(currentAtk, spriteName, atkFrames[i], atkTics[i], currentAtkAction, nextAtk);
            if (currentAtk == atkCycleEnd) break;
            if (i < atkFrames.length - 1) currentAtk = nextAtk;
        }

        // Pain
        addState(pain, spriteName, painFrame, painTics, painAction, run1); // Default to run after pain
        if (painEnd != null && painEnd != pain) { // If pain has multiple frames
            addState(StateNum.values()[pain.ordinal() + 1], spriteName, painFrame, painTics, painAction, run1);
        }


        // Die cycle
        StateNum currentDie = die1;
        for (int i = 0; i < dieFrames.length; i++) {
            boolean isLastFrame = (i == dieFrames.length - 1 || currentDie == dieCycleEnd);
            StateNum nextDie = isLastFrame ? StateNum.S_NULL : StateNum.values()[currentDie.ordinal() + 1];
            MobjAction currentDieAction = (dieActions != null && i < dieActions.length && dieActions[i] != null) ? dieActions[i] : Actions.NULL_ACTION;
            addState(currentDie, spriteName, dieFrames[i], dieTics[i], currentDieAction, nextDie);
            if (currentDie == dieCycleEnd) break;
            if (i < dieFrames.length - 1) currentDie = nextDie;
        }

        // Xtreme Die cycle (if applicable)
        if (xdie1 != null) {
            StateNum currentXDie = xdie1;
            for (int i = 0; i < xdieFrames.length; i++) {
                boolean isLastFrame = (i == xdieFrames.length - 1 || currentXDie == xdieCycleEnd);
                StateNum nextXDie = isLastFrame ? StateNum.S_NULL : StateNum.values()[currentXDie.ordinal() + 1];
                MobjAction currentXDieAction = (xdieActions != null && i < xdieActions.length && xdieActions[i] != null) ? xdieActions[i] : Actions.NULL_ACTION;
                addState(currentXDie, spriteName, xdieFrames[i], xdieTics[i], currentXDieAction, nextXDie);
                if (currentXDie == xdieCycleEnd) break;
                if (i < xdieFrames.length - 1) currentXDie = nextXDie;
            }
        }

        // Raise cycle (if applicable)
        if (raise1 != null) {
            StateNum currentRaise = raise1;
            for (int i = 0; i < raiseFrames.length; i++) {
                // Raise frames are often reverse of die frames
                boolean isLastFrame = (i == raiseFrames.length - 1 || currentRaise == raiseCycleEnd);
                StateNum nextRaise = isLastFrame ? run1 : StateNum.values()[currentRaise.ordinal() + 1]; // Default to run after raise
                addState(currentRaise, spriteName, raiseFrames[i], raiseTics[i], Actions.NULL_ACTION, nextRaise);
                if (currentRaise == raiseCycleEnd) break;
                if (i < raiseFrames.length - 1) currentRaise = nextRaise;
            }
        }
    }


    private void populatePossessed() { // Zombieman (MT_POSSESSED)
        // States: S_POSS_STND (160) to S_POSS_RAISE4 (186)
        // Sprite: SPR_POSS (index 29)
        // Frames in info.c: 0-based for POSS sprites
        // Example: S_POSS_STND: sprite SPR_POSS, frame 0. Action A_Look. Next S_POSS_STND2
        //          S_POSS_STND2: sprite SPR_POSS, frame 1. Action A_Look. Next S_POSS_STND
        addState(StateNum.S_POSS_STND, SpriteNames.POSS, 0, 10, Actions::A_Look, StateNum.S_POSS_STND2);
        addState(StateNum.S_POSS_STND2, SpriteNames.POSS, 1, 10, Actions::A_Look, StateNum.S_POSS_STND);

        // Run: 8 frames (0,0,1,1,2,2,3,3), 4 tics each
        addState(StateNum.S_POSS_RUN1, SpriteNames.POSS, 0, 4, Actions::A_Chase, StateNum.S_POSS_RUN2);
        addState(StateNum.S_POSS_RUN2, SpriteNames.POSS, 0, 4, Actions::A_Chase, StateNum.S_POSS_RUN3);
        addState(StateNum.S_POSS_RUN3, SpriteNames.POSS, 1, 4, Actions::A_Chase, StateNum.S_POSS_RUN4);
        addState(StateNum.S_POSS_RUN4, SpriteNames.POSS, 1, 4, Actions::A_Chase, StateNum.S_POSS_RUN5);
        addState(StateNum.S_POSS_RUN5, SpriteNames.POSS, 2, 4, Actions::A_Chase, StateNum.S_POSS_RUN6);
        addState(StateNum.S_POSS_RUN6, SpriteNames.POSS, 2, 4, Actions::A_Chase, StateNum.S_POSS_RUN7);
        addState(StateNum.S_POSS_RUN7, SpriteNames.POSS, 3, 4, Actions::A_Chase, StateNum.S_POSS_RUN8);
        addState(StateNum.S_POSS_RUN8, SpriteNames.POSS, 3, 4, Actions::A_Chase, StateNum.S_POSS_RUN1);

        // Attack: 3 frames (4,5,4), tics (10,8,8)
        addState(StateNum.S_POSS_ATK1, SpriteNames.POSS, 4, 10, Actions::A_FaceTarget, StateNum.S_POSS_ATK2);
        addState(StateNum.S_POSS_ATK2, SpriteNames.POSS, 5, 8, Actions::A_PosAttack, StateNum.S_POSS_ATK3);
        addState(StateNum.S_POSS_ATK3, SpriteNames.POSS, 4, 8, Actions.NULL_ACTION, StateNum.S_POSS_RUN1);

        // Pain: 2 frames (6,6), tics (3,3)
        addState(StateNum.S_POSS_PAIN, SpriteNames.POSS, 6, 3, Actions.NULL_ACTION, StateNum.S_POSS_PAIN2);
        addState(StateNum.S_POSS_PAIN2, SpriteNames.POSS, 6, 3, Actions::A_Pain, StateNum.S_POSS_RUN1);

        // Die: 5 frames (7,8,9,10,11), tics (5,5,5,5,-1)
        addState(StateNum.S_POSS_DIE1, SpriteNames.POSS, 7, 5, Actions.NULL_ACTION, StateNum.S_POSS_DIE2);
        addState(StateNum.S_POSS_DIE2, SpriteNames.POSS, 8, 5, Actions::A_Scream, StateNum.S_POSS_DIE3);
        addState(StateNum.S_POSS_DIE3, SpriteNames.POSS, 9, 5, Actions::A_Fall, StateNum.S_POSS_DIE4);
        addState(StateNum.S_POSS_DIE4, SpriteNames.POSS, 10, 5, Actions.NULL_ACTION, StateNum.S_POSS_DIE5);
        addState(StateNum.S_POSS_DIE5, SpriteNames.POSS, 11, -1, Actions.NULL_ACTION, StateNum.S_NULL); // Stays in this frame

        // XDeath: 9 frames (12 to 20), tics (5...5, -1)
        addState(StateNum.S_POSS_XDIE1, SpriteNames.POSS, 12, 5, Actions.NULL_ACTION, StateNum.S_POSS_XDIE2);
        addState(StateNum.S_POSS_XDIE2, SpriteNames.POSS, 13, 5, Actions::A_XScream, StateNum.S_POSS_XDIE3);
        addState(StateNum.S_POSS_XDIE3, SpriteNames.POSS, 14, 5, Actions::A_Fall, StateNum.S_POSS_XDIE4);
        addState(StateNum.S_POSS_XDIE4, SpriteNames.POSS, 15, 5, Actions.NULL_ACTION, StateNum.S_POSS_XDIE5);
        addState(StateNum.S_POSS_XDIE5, SpriteNames.POSS, 16, 5, Actions.NULL_ACTION, StateNum.S_POSS_XDIE6);
        addState(StateNum.S_POSS_XDIE6, SpriteNames.POSS, 17, 5, Actions.NULL_ACTION, StateNum.S_POSS_XDIE7);
        addState(StateNum.S_POSS_XDIE7, SpriteNames.POSS, 18, 5, Actions.NULL_ACTION, StateNum.S_POSS_XDIE8);
        addState(StateNum.S_POSS_XDIE8, SpriteNames.POSS, 19, 5, Actions.NULL_ACTION, StateNum.S_POSS_XDIE9);
        addState(StateNum.S_POSS_XDIE9, SpriteNames.POSS, 20, -1, Actions.NULL_ACTION, StateNum.S_NULL);

        // Raise: 4 frames (10,9,8,7), tics (5,5,5,5)
        addState(StateNum.S_POSS_RAISE1, SpriteNames.POSS, 10, 5, Actions.NULL_ACTION, StateNum.S_POSS_RAISE2);
        addState(StateNum.S_POSS_RAISE2, SpriteNames.POSS, 9, 5, Actions.NULL_ACTION, StateNum.S_POSS_RAISE3);
        addState(StateNum.S_POSS_RAISE3, SpriteNames.POSS, 8, 5, Actions.NULL_ACTION, StateNum.S_POSS_RAISE4);
        addState(StateNum.S_POSS_RAISE4, SpriteNames.POSS, 7, 5, Actions.NULL_ACTION, StateNum.S_POSS_RUN1);

        // MobjInfo for MT_POSSESSED (Zombieman)
        MobjInfoDef possessedInfo = new MobjInfoDef(
                "MT_POSSESSED", TYPE_POSSESSED, StateNum.S_POSS_STND, 20, StateNum.S_POSS_RUN1,
                8, StateNum.S_POSS_PAIN, 200, StateNum.S_NULL, // Melee state S_NULL
                StateNum.S_POSS_ATK1, StateNum.S_POSS_DIE1, StateNum.S_POSS_XDIE1,
                8, 20.0, 56.0, 100, 0, // speed, radius, height, mass, damage
                MobjFlags.MF_SOLID | MobjFlags.MF_SHOOTABLE | MobjFlags.MF_COUNTKILL,
                StateNum.S_POSS_RAISE1
        );
        mobjInfos.put(MobjType.MT_POSSESSED, possessedInfo);
        doomedNumToMobjType.put(TYPE_POSSESSED, MobjType.MT_POSSESSED);
    }

    private void populateShotguy() { // Shotgun Guy (MT_SHOTGUY)
        // States: S_SPOS_STND (187) to S_SPOS_RAISE4 (213)
        // Sprite: SPR_SPOS (index 30)
        addState(StateNum.S_SPOS_STND, SpriteNames.SPOS, 0, 10, Actions::A_Look, StateNum.S_SPOS_STND2);
        addState(StateNum.S_SPOS_STND2, SpriteNames.SPOS, 1, 10, Actions::A_Look, StateNum.S_SPOS_STND);

        // Run: 8 frames (0,0,1,1,2,2,3,3), 3 tics each (faster than Zombieman)
        addState(StateNum.S_SPOS_RUN1, SpriteNames.SPOS, 0, 3, Actions::A_Chase, StateNum.S_SPOS_RUN2);
        addState(StateNum.S_SPOS_RUN2, SpriteNames.SPOS, 0, 3, Actions::A_Chase, StateNum.S_SPOS_RUN3);
        addState(StateNum.S_SPOS_RUN3, SpriteNames.SPOS, 1, 3, Actions::A_Chase, StateNum.S_SPOS_RUN4);
        addState(StateNum.S_SPOS_RUN4, SpriteNames.SPOS, 1, 3, Actions::A_Chase, StateNum.S_SPOS_RUN5);
        addState(StateNum.S_SPOS_RUN5, SpriteNames.SPOS, 2, 3, Actions::A_Chase, StateNum.S_SPOS_RUN6);
        addState(StateNum.S_SPOS_RUN6, SpriteNames.SPOS, 2, 3, Actions::A_Chase, StateNum.S_SPOS_RUN7);
        addState(StateNum.S_SPOS_RUN7, SpriteNames.SPOS, 3, 3, Actions::A_Chase, StateNum.S_SPOS_RUN8);
        addState(StateNum.S_SPOS_RUN8, SpriteNames.SPOS, 3, 3, Actions::A_Chase, StateNum.S_SPOS_RUN1);

        // Attack: 3 frames (4,5,4), tics (10,10,10) - shotgun attack pattern
        addState(StateNum.S_SPOS_ATK1, SpriteNames.SPOS, 4, 10, Actions::A_FaceTarget, StateNum.S_SPOS_ATK2);
        addState(StateNum.S_SPOS_ATK2, SpriteNames.SPOS, 5, 10, Actions::A_SPosAttack, StateNum.S_SPOS_ATK3);
        addState(StateNum.S_SPOS_ATK3, SpriteNames.SPOS, 4, 10, Actions.NULL_ACTION, StateNum.S_SPOS_RUN1);

        // Pain: 2 frames (6,6), tics (3,3)
        addState(StateNum.S_SPOS_PAIN, SpriteNames.SPOS, 6, 3, Actions.NULL_ACTION, StateNum.S_SPOS_PAIN2);
        addState(StateNum.S_SPOS_PAIN2, SpriteNames.SPOS, 6, 3, Actions::A_Pain, StateNum.S_SPOS_RUN1);

        // Die: 5 frames (7,8,9,10,11), tics (5,5,5,5,-1)
        addState(StateNum.S_SPOS_DIE1, SpriteNames.SPOS, 7, 5, Actions.NULL_ACTION, StateNum.S_SPOS_DIE2);
        addState(StateNum.S_SPOS_DIE2, SpriteNames.SPOS, 8, 5, Actions::A_Scream, StateNum.S_SPOS_DIE3);
        addState(StateNum.S_SPOS_DIE3, SpriteNames.SPOS, 9, 5, Actions::A_Fall, StateNum.S_SPOS_DIE4);
        addState(StateNum.S_SPOS_DIE4, SpriteNames.SPOS, 10, 5, Actions.NULL_ACTION, StateNum.S_SPOS_DIE5);
        addState(StateNum.S_SPOS_DIE5, SpriteNames.SPOS, 11, -1, Actions.NULL_ACTION, StateNum.S_NULL);

        // XDeath: 9 frames (12 to 20), tics (5...5, -1)
        addState(StateNum.S_SPOS_XDIE1, SpriteNames.SPOS, 12, 5, Actions.NULL_ACTION, StateNum.S_SPOS_XDIE2);
        addState(StateNum.S_SPOS_XDIE2, SpriteNames.SPOS, 13, 5, Actions::A_XScream, StateNum.S_SPOS_XDIE3);
        addState(StateNum.S_SPOS_XDIE3, SpriteNames.SPOS, 14, 5, Actions::A_Fall, StateNum.S_SPOS_XDIE4);
        addState(StateNum.S_SPOS_XDIE4, SpriteNames.SPOS, 15, 5, Actions.NULL_ACTION, StateNum.S_SPOS_XDIE5);
        addState(StateNum.S_SPOS_XDIE5, SpriteNames.SPOS, 16, 5, Actions.NULL_ACTION, StateNum.S_SPOS_XDIE6);
        addState(StateNum.S_SPOS_XDIE6, SpriteNames.SPOS, 17, 5, Actions.NULL_ACTION, StateNum.S_SPOS_XDIE7);
        addState(StateNum.S_SPOS_XDIE7, SpriteNames.SPOS, 18, 5, Actions.NULL_ACTION, StateNum.S_SPOS_XDIE8);
        addState(StateNum.S_SPOS_XDIE8, SpriteNames.SPOS, 19, 5, Actions.NULL_ACTION, StateNum.S_SPOS_XDIE9);
        addState(StateNum.S_SPOS_XDIE9, SpriteNames.SPOS, 20, -1, Actions.NULL_ACTION, StateNum.S_NULL);

        // Raise: 4 frames (10,9,8,7), tics (5,5,5,5)
        addState(StateNum.S_SPOS_RAISE1, SpriteNames.SPOS, 10, 5, Actions.NULL_ACTION, StateNum.S_SPOS_RAISE2);
        addState(StateNum.S_SPOS_RAISE2, SpriteNames.SPOS, 9, 5, Actions.NULL_ACTION, StateNum.S_SPOS_RAISE3);
        addState(StateNum.S_SPOS_RAISE3, SpriteNames.SPOS, 8, 5, Actions.NULL_ACTION, StateNum.S_SPOS_RAISE4);
        addState(StateNum.S_SPOS_RAISE4, SpriteNames.SPOS, 7, 5, Actions.NULL_ACTION, StateNum.S_SPOS_RUN1);

        // MobjInfo for MT_SHOTGUY (Shotgun Guy)
        MobjInfoDef shotguyInfo = new MobjInfoDef(
                "MT_SHOTGUY", TYPE_SHOTGUY, StateNum.S_SPOS_STND, 30, StateNum.S_SPOS_RUN1,
                8, StateNum.S_SPOS_PAIN, 170, StateNum.S_NULL, // Melee state S_NULL
                StateNum.S_SPOS_ATK1, StateNum.S_SPOS_DIE1, StateNum.S_SPOS_XDIE1,
                8, 20.0, 56.0, 100, 0, // speed, radius, height, mass, damage
                MobjFlags.MF_SOLID | MobjFlags.MF_SHOOTABLE | MobjFlags.MF_COUNTKILL,
                StateNum.S_SPOS_RAISE1
        );
        mobjInfos.put(MobjType.MT_SHOTGUY, shotguyInfo);
        doomedNumToMobjType.put(TYPE_SHOTGUY, MobjType.MT_SHOTGUY);
    }

    private void populateImp() { // Imp (MT_TROOP)
        // States: S_TROO_STND (214) to S_TROO_RAISE5 (252)
        // Sprite: SPR_TROO (index 31)
        addState(StateNum.S_TROO_STND, SpriteNames.TROO, 0, 10, Actions::A_Look, StateNum.S_TROO_STND2);
        addState(StateNum.S_TROO_STND2, SpriteNames.TROO, 1, 10, Actions::A_Look, StateNum.S_TROO_STND);

        // Run: 8 frames (0,0,1,1,2,2,3,3), 3 tics each
        addState(StateNum.S_TROO_RUN1, SpriteNames.TROO, 0, 3, Actions::A_Chase, StateNum.S_TROO_RUN2);
        addState(StateNum.S_TROO_RUN2, SpriteNames.TROO, 0, 3, Actions::A_Chase, StateNum.S_TROO_RUN3);
        addState(StateNum.S_TROO_RUN3, SpriteNames.TROO, 1, 3, Actions::A_Chase, StateNum.S_TROO_RUN4);
        addState(StateNum.S_TROO_RUN4, SpriteNames.TROO, 1, 3, Actions::A_Chase, StateNum.S_TROO_RUN5);
        addState(StateNum.S_TROO_RUN5, SpriteNames.TROO, 2, 3, Actions::A_Chase, StateNum.S_TROO_RUN6);
        addState(StateNum.S_TROO_RUN6, SpriteNames.TROO, 2, 3, Actions::A_Chase, StateNum.S_TROO_RUN7);
        addState(StateNum.S_TROO_RUN7, SpriteNames.TROO, 3, 3, Actions::A_Chase, StateNum.S_TROO_RUN8);
        addState(StateNum.S_TROO_RUN8, SpriteNames.TROO, 3, 3, Actions::A_Chase, StateNum.S_TROO_RUN1);

        // Melee Attack: 3 frames (6,7,6), tics (8,8,6) - TROOG, TROOH, TROOG
        addState(StateNum.S_TROO_ATK1, SpriteNames.TROO, 6, 8, Actions::A_FaceTarget, StateNum.S_TROO_ATK2);
        addState(StateNum.S_TROO_ATK2, SpriteNames.TROO, 7, 8, Actions::A_FaceTarget, StateNum.S_TROO_ATK3);
        addState(StateNum.S_TROO_ATK3, SpriteNames.TROO, 6, 6, Actions::A_TroopAttack, StateNum.S_TROO_RUN1);

        // Missile Attack: 3 frames (8,9,8), tics (8,8,8) - TROOI, TROOJ, TROOI
        addState(StateNum.S_TROO_MISS1, SpriteNames.TROO, 8, 8, Actions::A_FaceTarget, StateNum.S_TROO_MISS2);
        addState(StateNum.S_TROO_MISS2, SpriteNames.TROO, 9, 8, Actions::A_TroopMissile, StateNum.S_TROO_MISS3);
        addState(StateNum.S_TROO_MISS3, SpriteNames.TROO, 8, 8, Actions.NULL_ACTION, StateNum.S_TROO_RUN1);

        // Pain: 2 frames (10,10), tics (2,2) - TROOK
        addState(StateNum.S_TROO_PAIN, SpriteNames.TROO, 10, 2, Actions.NULL_ACTION, StateNum.S_TROO_PAIN2);
        addState(StateNum.S_TROO_PAIN2, SpriteNames.TROO, 10, 2, Actions::A_Pain, StateNum.S_TROO_RUN1);

        // Die: 6 frames (11,12,13,14,15,16), tics (8,8,6,6,6,-1) - TROOL to TROOQ
        addState(StateNum.S_TROO_DIE1, SpriteNames.TROO, 11, 8, Actions.NULL_ACTION, StateNum.S_TROO_DIE2);
        addState(StateNum.S_TROO_DIE2, SpriteNames.TROO, 12, 8, Actions::A_Scream, StateNum.S_TROO_DIE3);
        addState(StateNum.S_TROO_DIE3, SpriteNames.TROO, 13, 6, Actions.NULL_ACTION, StateNum.S_TROO_DIE4);
        addState(StateNum.S_TROO_DIE4, SpriteNames.TROO, 14, 6, Actions::A_Fall, StateNum.S_TROO_DIE5);
        addState(StateNum.S_TROO_DIE5, SpriteNames.TROO, 15, 6, Actions.NULL_ACTION, StateNum.S_TROO_DIE6);
        addState(StateNum.S_TROO_DIE6, SpriteNames.TROO, 16, -1, Actions.NULL_ACTION, StateNum.S_NULL);

        // XDeath: 8 frames (17,18,19,20,21,22,23,24), tics (5,5,5,5,5,5,5,-1) - TROOR to TROOY
        addState(StateNum.S_TROO_XDIE1, SpriteNames.TROO, 17, 5, Actions.NULL_ACTION, StateNum.S_TROO_XDIE2);
        addState(StateNum.S_TROO_XDIE2, SpriteNames.TROO, 18, 5, Actions::A_XScream, StateNum.S_TROO_XDIE3);
        addState(StateNum.S_TROO_XDIE3, SpriteNames.TROO, 19, 5, Actions.NULL_ACTION, StateNum.S_TROO_XDIE4);
        addState(StateNum.S_TROO_XDIE4, SpriteNames.TROO, 20, 5, Actions::A_Fall, StateNum.S_TROO_XDIE5);
        addState(StateNum.S_TROO_XDIE5, SpriteNames.TROO, 21, 5, Actions.NULL_ACTION, StateNum.S_TROO_XDIE6);
        addState(StateNum.S_TROO_XDIE6, SpriteNames.TROO, 22, 5, Actions.NULL_ACTION, StateNum.S_TROO_XDIE7);
        addState(StateNum.S_TROO_XDIE7, SpriteNames.TROO, 23, 5, Actions.NULL_ACTION, StateNum.S_TROO_XDIE8);
        addState(StateNum.S_TROO_XDIE8, SpriteNames.TROO, 24, -1, Actions.NULL_ACTION, StateNum.S_NULL);

        // Raise: 5 frames (16,15,14,13,12), tics (8,8,6,6,8) - Reverse of death sequence
        addState(StateNum.S_TROO_RAISE1, SpriteNames.TROO, 16, 8, Actions.NULL_ACTION, StateNum.S_TROO_RAISE2);
        addState(StateNum.S_TROO_RAISE2, SpriteNames.TROO, 15, 8, Actions.NULL_ACTION, StateNum.S_TROO_RAISE3);
        addState(StateNum.S_TROO_RAISE3, SpriteNames.TROO, 14, 6, Actions.NULL_ACTION, StateNum.S_TROO_RAISE4);
        addState(StateNum.S_TROO_RAISE4, SpriteNames.TROO, 13, 6, Actions.NULL_ACTION, StateNum.S_TROO_RAISE5);
        addState(StateNum.S_TROO_RAISE5, SpriteNames.TROO, 12, 8, Actions.NULL_ACTION, StateNum.S_TROO_RUN1);

        // MobjInfo for MT_TROOP (Imp)
        MobjInfoDef troopInfo = new MobjInfoDef(
                "MT_TROOP", TYPE_TROOP, StateNum.S_TROO_STND, 60, StateNum.S_TROO_RUN1,
                8, StateNum.S_TROO_PAIN, 200, StateNum.S_TROO_ATK1, // melee attack
                StateNum.S_TROO_MISS1, StateNum.S_TROO_DIE1, StateNum.S_TROO_XDIE1,
                8, 20.0, 56.0, 100, 3, // speed, radius, height, mass, damage (melee)
                MobjFlags.MF_SOLID | MobjFlags.MF_SHOOTABLE | MobjFlags.MF_COUNTKILL,
                StateNum.S_TROO_RAISE1
        );
        mobjInfos.put(MobjType.MT_TROOP, troopInfo);
        doomedNumToMobjType.put(TYPE_TROOP, MobjType.MT_TROOP);
    }

    private void populateDemon() { // Demon/Pinky (MT_SERGEANT)
        // States: S_SARG_STND (253) to S_SARG_RAISE6 (290)
        // Sprite: SPR_SARG (index 32)
        addState(StateNum.S_SARG_STND, SpriteNames.SARG, 0, 10, Actions::A_Look, StateNum.S_SARG_STND2);
        addState(StateNum.S_SARG_STND2, SpriteNames.SARG, 1, 10, Actions::A_Look, StateNum.S_SARG_STND);

        // Run: 8 frames (0,0,1,1,2,2,3,3), 2 tics each (fast)
        addState(StateNum.S_SARG_RUN1, SpriteNames.SARG, 0, 2, Actions::A_Chase, StateNum.S_SARG_RUN2);
        addState(StateNum.S_SARG_RUN2, SpriteNames.SARG, 0, 2, Actions::A_Chase, StateNum.S_SARG_RUN3);
        addState(StateNum.S_SARG_RUN3, SpriteNames.SARG, 1, 2, Actions::A_Chase, StateNum.S_SARG_RUN4);
        addState(StateNum.S_SARG_RUN4, SpriteNames.SARG, 1, 2, Actions::A_Chase, StateNum.S_SARG_RUN5);
        addState(StateNum.S_SARG_RUN5, SpriteNames.SARG, 2, 2, Actions::A_Chase, StateNum.S_SARG_RUN6);
        addState(StateNum.S_SARG_RUN6, SpriteNames.SARG, 2, 2, Actions::A_Chase, StateNum.S_SARG_RUN7);
        addState(StateNum.S_SARG_RUN7, SpriteNames.SARG, 3, 2, Actions::A_Chase, StateNum.S_SARG_RUN8);
        addState(StateNum.S_SARG_RUN8, SpriteNames.SARG, 3, 2, Actions::A_Chase, StateNum.S_SARG_RUN1);

        // Melee Attack: 4 frames (4,5,6,7), tics (8,8,8,8)
        addState(StateNum.S_SARG_ATK1, SpriteNames.SARG, 4, 8, Actions::A_FaceTarget, StateNum.S_SARG_ATK2);
        addState(StateNum.S_SARG_ATK2, SpriteNames.SARG, 5, 8, Actions::A_FaceTarget, StateNum.S_SARG_ATK3);
        addState(StateNum.S_SARG_ATK3, SpriteNames.SARG, 6, 8, Actions::A_SargAttack, StateNum.S_SARG_ATK4);
        addState(StateNum.S_SARG_ATK4, SpriteNames.SARG, 7, 8, Actions.NULL_ACTION, StateNum.S_SARG_RUN1);

        // Pain: 2 frames (8,8), tics (2,2)
        addState(StateNum.S_SARG_PAIN, SpriteNames.SARG, 8, 2, Actions.NULL_ACTION, StateNum.S_SARG_PAIN2);
        addState(StateNum.S_SARG_PAIN2, SpriteNames.SARG, 8, 2, Actions::A_Pain, StateNum.S_SARG_RUN1);

        // Die: 6 frames (9,10,11,12,13,14), tics (8,8,4,4,4,-1)
        addState(StateNum.S_SARG_DIE1, SpriteNames.SARG, 9, 8, Actions.NULL_ACTION, StateNum.S_SARG_DIE2);
        addState(StateNum.S_SARG_DIE2, SpriteNames.SARG, 10, 8, Actions::A_Scream, StateNum.S_SARG_DIE3);
        addState(StateNum.S_SARG_DIE3, SpriteNames.SARG, 11, 4, Actions.NULL_ACTION, StateNum.S_SARG_DIE4);
        addState(StateNum.S_SARG_DIE4, SpriteNames.SARG, 12, 4, Actions::A_Fall, StateNum.S_SARG_DIE5);
        addState(StateNum.S_SARG_DIE5, SpriteNames.SARG, 13, 4, Actions.NULL_ACTION, StateNum.S_SARG_DIE6);
        addState(StateNum.S_SARG_DIE6, SpriteNames.SARG, 14, -1, Actions.NULL_ACTION, StateNum.S_NULL);

        // Raise: 6 frames (13,12,11,10,9), tics (5,5,6,6,8)
        addState(StateNum.S_SARG_RAISE1, SpriteNames.SARG, 13, 5, Actions.NULL_ACTION, StateNum.S_SARG_RAISE2);
        addState(StateNum.S_SARG_RAISE2, SpriteNames.SARG, 12, 5, Actions.NULL_ACTION, StateNum.S_SARG_RAISE3);
        addState(StateNum.S_SARG_RAISE3, SpriteNames.SARG, 11, 6, Actions.NULL_ACTION, StateNum.S_SARG_RAISE4);
        addState(StateNum.S_SARG_RAISE4, SpriteNames.SARG, 10, 6, Actions.NULL_ACTION, StateNum.S_SARG_RAISE5);
        addState(StateNum.S_SARG_RAISE5, SpriteNames.SARG, 9, 8, Actions.NULL_ACTION, StateNum.S_SARG_RUN1);

        // MobjInfo for MT_SERGEANT (Demon/Pinky)
        MobjInfoDef demonInfo = new MobjInfoDef(
                "MT_SERGEANT", TYPE_SERGEANT, StateNum.S_SARG_STND, 150, StateNum.S_SARG_RUN1,
                8, StateNum.S_SARG_PAIN, 180, StateNum.S_SARG_ATK1, // melee only
                StateNum.S_NULL, StateNum.S_SARG_DIE1, StateNum.S_NULL, // no xdeath
                10, 30.0, 56.0, 400, 4, // speed, radius, height, mass, damage (strong bite)
                MobjFlags.MF_SOLID | MobjFlags.MF_SHOOTABLE | MobjFlags.MF_COUNTKILL,
                StateNum.S_SARG_RAISE1
        );
        mobjInfos.put(MobjType.MT_SERGEANT, demonInfo);
        doomedNumToMobjType.put(TYPE_SERGEANT, MobjType.MT_SERGEANT);
    }

    private void populateProjectiles() {
        // MT_TROOPSHOT (Imp Fireball) states
        addState(StateNum.S_TBALL1, SpriteNames.BAL1, 0, 4, Actions.NULL_ACTION, StateNum.S_TBALL2);
        addState(StateNum.S_TBALL2, SpriteNames.BAL1, 1, 4, Actions.NULL_ACTION, StateNum.S_TBALL1);
        
        // Fireball explosion
        addState(StateNum.S_TBALLEX1, SpriteNames.BAL1, 2, 6, Actions.NULL_ACTION, StateNum.S_TBALLEX2);
        addState(StateNum.S_TBALLEX2, SpriteNames.BAL1, 3, 6, Actions.NULL_ACTION, StateNum.S_TBALLEX3);
        addState(StateNum.S_TBALLEX3, SpriteNames.BAL1, 4, 6, Actions.NULL_ACTION, StateNum.S_NULL);

        // MobjInfo for MT_TROOPSHOT (Imp Fireball)
        MobjInfoDef fireballInfo = new MobjInfoDef(
                "MT_TROOPSHOT", -1, StateNum.S_TBALL1, 1000, StateNum.S_NULL,
                8, StateNum.S_NULL, 0, StateNum.S_NULL,
                StateNum.S_NULL, StateNum.S_TBALLEX1, StateNum.S_NULL,
                10, 6.0, 8.0, 100, 3, // speed, radius, height, mass, damage
                MobjFlags.MF_NOBLOCKMAP | MobjFlags.MF_MISSILE | MobjFlags.MF_DROPOFF | MobjFlags.MF_NOGRAVITY,
                StateNum.S_NULL
        );
        mobjInfos.put(MobjType.MT_TROOPSHOT, fireballInfo);

        // MT_PUFF (Bullet Impact) states
        addState(StateNum.S_PUFF1, SpriteNames.PUFF, 0, 4, Actions.NULL_ACTION, StateNum.S_PUFF2);
        addState(StateNum.S_PUFF2, SpriteNames.PUFF, 1, 4, Actions.NULL_ACTION, StateNum.S_PUFF3);
        addState(StateNum.S_PUFF3, SpriteNames.PUFF, 2, 4, Actions.NULL_ACTION, StateNum.S_PUFF4);
        addState(StateNum.S_PUFF4, SpriteNames.PUFF, 3, 4, Actions.NULL_ACTION, StateNum.S_NULL);

        // MobjInfo for MT_PUFF (Bullet Impact)
        MobjInfoDef puffInfo = new MobjInfoDef(
                "MT_PUFF", -1, StateNum.S_PUFF1, 1000, StateNum.S_NULL,
                0, StateNum.S_NULL, 0, StateNum.S_NULL,
                StateNum.S_NULL, StateNum.S_NULL, StateNum.S_NULL,
                0, 20.0, 16.0, 100, 0, // no speed, radius, height, mass, no damage
                MobjFlags.MF_NOBLOCKMAP | MobjFlags.MF_NOGRAVITY,
                StateNum.S_NULL
        );
        mobjInfos.put(MobjType.MT_PUFF, puffInfo);

        // MT_BLOOD (Blood Splat) states
        addState(StateNum.S_BLOOD1, SpriteNames.BLUD, 2, 8, Actions.NULL_ACTION, StateNum.S_BLOOD2);
        addState(StateNum.S_BLOOD2, SpriteNames.BLUD, 1, 8, Actions.NULL_ACTION, StateNum.S_BLOOD3);
        addState(StateNum.S_BLOOD3, SpriteNames.BLUD, 0, 8, Actions.NULL_ACTION, StateNum.S_NULL);

        // MobjInfo for MT_BLOOD (Blood Splat)
        MobjInfoDef bloodInfo = new MobjInfoDef(
                "MT_BLOOD", -1, StateNum.S_BLOOD1, 1000, StateNum.S_NULL,
                0, StateNum.S_NULL, 0, StateNum.S_NULL,
                StateNum.S_NULL, StateNum.S_NULL, StateNum.S_NULL,
                0, 20.0, 16.0, 100, 0, // no speed, radius, height, mass, no damage
                MobjFlags.MF_NOBLOCKMAP | MobjFlags.MF_NOGRAVITY,
                StateNum.S_NULL
        );
        mobjInfos.put(MobjType.MT_BLOOD, bloodInfo);
    }


    public StateDef getState(StateNum stateNum) {
        if (stateNum == null || !statesMap.containsKey(stateNum)) {
            return statesMap.get(StateNum.S_NULL); // Fallback to S_NULL if defined, otherwise could be null
        }
        return statesMap.get(stateNum);
    }

    public MobjInfoDef getMobjInfo(MobjType type) {
        return mobjInfos.get(type);
    }

    public MobjInfoDef getMobjInfoByDoomedNum(int doomedNum) {
        MobjType type = doomedNumToMobjType.get(doomedNum);
        if (type != null) {
            return mobjInfos.get(type);
        }
        return null;
    }
}

