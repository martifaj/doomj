package com.doomviewer.game.objects;

import com.doomviewer.audio.SoundKey;

import java.util.HashMap;
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

        // Initialize with S_NULL state at index 0 - should not render any sprite
        statesMap.put(StateNum.S_NULL, new StateDef(SpriteNames.TROO, -1, -1, Actions.NULL, StateNum.S_NULL)); // S_NULL with invalid frame

        populatePlayer(); // Add this call
        populatePossessed(); // Zombieman
        populateShotguy();   // Shotgun Guy
        populateImp();       // Imp
        populateDemon();     // Demon/Spectre
        populateCacodemon(); // Cacodemon
        populateBaronOfHell(); // Baron of Hell & Hell Knight
        populateLostSoul();  // Lost Soul
        populateChaingunner(); // Chaingunner
        populateSpectre();   // Spectre (invisible Demon)
        populateBigEnemies(); // Cyberdemon & Spider Mastermind
        populateProjectiles(); // Projectiles and effects
        populateWeapons();   // Weapon states
        populateItems();     // Health, ammo, armor, keys
        populateDecorations(); // Decorative objects
        populateKeys();     // Keys for doors
        populateSpecialObjects(); // Teleporters, multiplayer starts
        // Add more initializations for other mobj types and their states
    }

    private void populateCacodemon() {
        // Cacodemon states (floating enemy)
        addState(StateNum.S_HEAD_STND, SpriteNames.HEAD, 0, 10, Actions.A_LOOK, StateNum.S_HEAD_STND);
        addState(StateNum.S_HEAD_RUN1, SpriteNames.HEAD, 0, 3, Actions.A_CHASE, StateNum.S_HEAD_RUN1);

        // Attack states
        addState(StateNum.S_HEAD_ATK1, SpriteNames.HEAD, 1, 5, Actions.A_FACE_TARGET, StateNum.S_HEAD_ATK2);
        addState(StateNum.S_HEAD_ATK2, SpriteNames.HEAD, 2, 5, Actions.NULL, StateNum.S_HEAD_ATK3);
        addState(StateNum.S_HEAD_ATK3, SpriteNames.HEAD, 1 | 0x8000, 5, Actions.A_TROOP_MISSILE, StateNum.S_HEAD_RUN1);

        // Pain states
        addState(StateNum.S_HEAD_PAIN, SpriteNames.HEAD, 3, 3, Actions.NULL, StateNum.S_HEAD_PAIN2);
        addState(StateNum.S_HEAD_PAIN2, SpriteNames.HEAD, 3, 3, Actions.A_PAIN, StateNum.S_HEAD_PAIN3);
        addState(StateNum.S_HEAD_PAIN3, SpriteNames.HEAD, 4, 6, Actions.NULL, StateNum.S_HEAD_RUN1);

        // Death states
        addState(StateNum.S_HEAD_DIE1, SpriteNames.HEAD, 5, 8, Actions.NULL, StateNum.S_HEAD_DIE2);
        addState(StateNum.S_HEAD_DIE2, SpriteNames.HEAD, 6, 8, Actions.A_SCREAM, StateNum.S_HEAD_DIE3);
        addState(StateNum.S_HEAD_DIE3, SpriteNames.HEAD, 7, 8, Actions.NULL, StateNum.S_HEAD_DIE4);
        addState(StateNum.S_HEAD_DIE4, SpriteNames.HEAD, 8, 8, Actions.NULL, StateNum.S_HEAD_DIE5);
        addState(StateNum.S_HEAD_DIE5, SpriteNames.HEAD, 9, 8, Actions.A_FALL, StateNum.S_HEAD_DIE6);
        addState(StateNum.S_HEAD_DIE6, SpriteNames.HEAD, 10, -1, Actions.NULL, StateNum.S_HEAD_DIE6);

        // Raise states (for Arch-Vile)
        addState(StateNum.S_HEAD_RAISE1, SpriteNames.HEAD, 10, 8, Actions.NULL, StateNum.S_HEAD_RAISE2);
        addState(StateNum.S_HEAD_RAISE2, SpriteNames.HEAD, 9, 8, Actions.NULL, StateNum.S_HEAD_RAISE3);
        addState(StateNum.S_HEAD_RAISE3, SpriteNames.HEAD, 8, 8, Actions.NULL, StateNum.S_HEAD_RAISE4);
        addState(StateNum.S_HEAD_RAISE4, SpriteNames.HEAD, 7, 8, Actions.NULL, StateNum.S_HEAD_RAISE5);
        addState(StateNum.S_HEAD_RAISE5, SpriteNames.HEAD, 6, 8, Actions.NULL, StateNum.S_HEAD_RAISE6);
        addState(StateNum.S_HEAD_RAISE6, SpriteNames.HEAD, 5, 8, Actions.NULL, StateNum.S_HEAD_RUN1);

        // MobjInfo for MT_HEAD (Cacodemon)
        MobjInfoDef cacodeInfo = new MobjInfoDef(
                "MT_HEAD", 3005, StateNum.S_HEAD_STND, 400, StateNum.S_HEAD_RUN1,
                SoundKey.SFX_CACSIT, // seeSound
                8, // reactiontime
                SoundKey.SFX_FIRSHT, // attackSound
                StateNum.S_HEAD_PAIN, 128, // painstate, painchance
                SoundKey.SFX_PLPAIN, // painSound (using player pain for now)
                StateNum.S_NULL, // no melee
                StateNum.S_HEAD_ATK1, StateNum.S_HEAD_DIE1, StateNum.S_NULL, // missile, death, xdeath
                SoundKey.SFX_CACSIT, // deathSound (using see sound for now)
                8, 31.0, 56.0, 400, 15, // speed, radius, height, mass, damage
                SoundKey.SFX_NONE, // activeSound
                MobjFlags.MF_SOLID | MobjFlags.MF_SHOOTABLE | MobjFlags.MF_COUNTKILL | MobjFlags.MF_NOGRAVITY | MobjFlags.MF_FLOAT,
                StateNum.S_HEAD_RAISE1
        );
        mobjInfos.put(MobjType.MT_HEAD, cacodeInfo);
        doomedNumToMobjType.put(3005, MobjType.MT_HEAD);
    }

    private void populateBaronOfHell() {
        // Baron of Hell states
        addState(StateNum.S_BOSS_STND, SpriteNames.BOSS, 0, 10, Actions.A_LOOK, StateNum.S_BOSS_STND2);
        addState(StateNum.S_BOSS_STND2, SpriteNames.BOSS, 1, 10, Actions.A_LOOK, StateNum.S_BOSS_STND);
        addState(StateNum.S_BOSS_RUN1, SpriteNames.BOSS, 0, 3, Actions.A_CHASE, StateNum.S_BOSS_RUN2);
        addState(StateNum.S_BOSS_RUN2, SpriteNames.BOSS, 0, 3, Actions.A_CHASE, StateNum.S_BOSS_RUN3);
        addState(StateNum.S_BOSS_RUN3, SpriteNames.BOSS, 1, 3, Actions.A_CHASE, StateNum.S_BOSS_RUN4);
        addState(StateNum.S_BOSS_RUN4, SpriteNames.BOSS, 1, 3, Actions.A_CHASE, StateNum.S_BOSS_RUN5);
        addState(StateNum.S_BOSS_RUN5, SpriteNames.BOSS, 2, 3, Actions.A_CHASE, StateNum.S_BOSS_RUN6);
        addState(StateNum.S_BOSS_RUN6, SpriteNames.BOSS, 2, 3, Actions.A_CHASE, StateNum.S_BOSS_RUN7);
        addState(StateNum.S_BOSS_RUN7, SpriteNames.BOSS, 3, 3, Actions.A_CHASE, StateNum.S_BOSS_RUN8);
        addState(StateNum.S_BOSS_RUN8, SpriteNames.BOSS, 3, 3, Actions.A_CHASE, StateNum.S_BOSS_RUN1);

        // Attack states
        addState(StateNum.S_BOSS_ATK1, SpriteNames.BOSS, 4, 8, Actions.A_FACE_TARGET, StateNum.S_BOSS_ATK2);
        addState(StateNum.S_BOSS_ATK2, SpriteNames.BOSS, 5, 8, Actions.A_TROOP_MISSILE, StateNum.S_BOSS_ATK3);
        addState(StateNum.S_BOSS_ATK3, SpriteNames.BOSS, 4, 8, Actions.A_FACE_TARGET, StateNum.S_BOSS_RUN1);

        // Pain states
        addState(StateNum.S_BOSS_PAIN, SpriteNames.BOSS, 6, 2, Actions.NULL, StateNum.S_BOSS_PAIN2);
        addState(StateNum.S_BOSS_PAIN2, SpriteNames.BOSS, 6, 2, Actions.A_PAIN, StateNum.S_BOSS_RUN1);

        // Death states
        addState(StateNum.S_BOSS_DIE1, SpriteNames.BOSS, 7, 8, Actions.NULL, StateNum.S_BOSS_DIE2);
        addState(StateNum.S_BOSS_DIE2, SpriteNames.BOSS, 8, 8, Actions.A_SCREAM, StateNum.S_BOSS_DIE3);
        addState(StateNum.S_BOSS_DIE3, SpriteNames.BOSS, 9, 8, Actions.NULL, StateNum.S_BOSS_DIE4);
        addState(StateNum.S_BOSS_DIE4, SpriteNames.BOSS, 10, 8, Actions.A_FALL, StateNum.S_BOSS_DIE5);
        addState(StateNum.S_BOSS_DIE5, SpriteNames.BOSS, 11, 8, Actions.NULL, StateNum.S_BOSS_DIE6);
        addState(StateNum.S_BOSS_DIE6, SpriteNames.BOSS, 12, 8, Actions.NULL, StateNum.S_BOSS_DIE7);
        addState(StateNum.S_BOSS_DIE7, SpriteNames.BOSS, 13, -1, Actions.NULL, StateNum.S_BOSS_DIE7);

        // Raise states
        addState(StateNum.S_BOSS_RAISE1, SpriteNames.BOSS, 13, 8, Actions.NULL, StateNum.S_BOSS_RAISE2);
        addState(StateNum.S_BOSS_RAISE2, SpriteNames.BOSS, 12, 8, Actions.NULL, StateNum.S_BOSS_RAISE3);
        addState(StateNum.S_BOSS_RAISE3, SpriteNames.BOSS, 11, 8, Actions.NULL, StateNum.S_BOSS_RAISE4);
        addState(StateNum.S_BOSS_RAISE4, SpriteNames.BOSS, 10, 8, Actions.NULL, StateNum.S_BOSS_RAISE5);
        addState(StateNum.S_BOSS_RAISE5, SpriteNames.BOSS, 9, 8, Actions.NULL, StateNum.S_BOSS_RAISE6);
        addState(StateNum.S_BOSS_RAISE6, SpriteNames.BOSS, 8, 8, Actions.NULL, StateNum.S_BOSS_RAISE7);
        addState(StateNum.S_BOSS_RAISE7, SpriteNames.BOSS, 7, 8, Actions.NULL, StateNum.S_BOSS_RUN1);

        // Hell Knight states (BOS2) - similar to Baron but different sprite
        addState(StateNum.S_BOS2_STND, SpriteNames.BOS2, 0, 10, Actions.A_LOOK, StateNum.S_BOS2_STND2);
        addState(StateNum.S_BOS2_STND2, SpriteNames.BOS2, 1, 10, Actions.A_LOOK, StateNum.S_BOS2_STND);
        addState(StateNum.S_BOS2_RUN1, SpriteNames.BOS2, 0, 3, Actions.A_CHASE, StateNum.S_BOS2_RUN2);
        addState(StateNum.S_BOS2_RUN2, SpriteNames.BOS2, 0, 3, Actions.A_CHASE, StateNum.S_BOS2_RUN3);
        addState(StateNum.S_BOS2_RUN3, SpriteNames.BOS2, 1, 3, Actions.A_CHASE, StateNum.S_BOS2_RUN4);
        addState(StateNum.S_BOS2_RUN4, SpriteNames.BOS2, 1, 3, Actions.A_CHASE, StateNum.S_BOS2_RUN5);
        addState(StateNum.S_BOS2_RUN5, SpriteNames.BOS2, 2, 3, Actions.A_CHASE, StateNum.S_BOS2_RUN6);
        addState(StateNum.S_BOS2_RUN6, SpriteNames.BOS2, 2, 3, Actions.A_CHASE, StateNum.S_BOS2_RUN7);
        addState(StateNum.S_BOS2_RUN7, SpriteNames.BOS2, 3, 3, Actions.A_CHASE, StateNum.S_BOS2_RUN8);
        addState(StateNum.S_BOS2_RUN8, SpriteNames.BOS2, 3, 3, Actions.A_CHASE, StateNum.S_BOS2_RUN1);

        // Hell Knight attack states
        addState(StateNum.S_BOS2_ATK1, SpriteNames.BOS2, 4, 8, Actions.A_FACE_TARGET, StateNum.S_BOS2_ATK2);
        addState(StateNum.S_BOS2_ATK2, SpriteNames.BOS2, 5, 8, Actions.A_TROOP_MISSILE, StateNum.S_BOS2_ATK3);
        addState(StateNum.S_BOS2_ATK3, SpriteNames.BOS2, 4, 8, Actions.A_FACE_TARGET, StateNum.S_BOS2_RUN1);

        // Hell Knight pain states
        addState(StateNum.S_BOS2_PAIN, SpriteNames.BOS2, 6, 2, Actions.NULL, StateNum.S_BOS2_PAIN2);
        addState(StateNum.S_BOS2_PAIN2, SpriteNames.BOS2, 6, 2, Actions.A_PAIN, StateNum.S_BOS2_RUN1);

        // Hell Knight death states
        addState(StateNum.S_BOS2_DIE1, SpriteNames.BOS2, 7, 8, Actions.NULL, StateNum.S_BOS2_DIE2);
        addState(StateNum.S_BOS2_DIE2, SpriteNames.BOS2, 8, 8, Actions.A_SCREAM, StateNum.S_BOS2_DIE3);
        addState(StateNum.S_BOS2_DIE3, SpriteNames.BOS2, 9, 8, Actions.NULL, StateNum.S_BOS2_DIE4);
        addState(StateNum.S_BOS2_DIE4, SpriteNames.BOS2, 10, 8, Actions.A_FALL, StateNum.S_BOS2_DIE5);
        addState(StateNum.S_BOS2_DIE5, SpriteNames.BOS2, 11, 8, Actions.NULL, StateNum.S_BOS2_DIE6);
        addState(StateNum.S_BOS2_DIE6, SpriteNames.BOS2, 12, 8, Actions.NULL, StateNum.S_BOS2_DIE7);
        addState(StateNum.S_BOS2_DIE7, SpriteNames.BOS2, 13, -1, Actions.NULL, StateNum.S_BOS2_DIE7);

        // Hell Knight raise states
        addState(StateNum.S_BOS2_RAISE1, SpriteNames.BOS2, 13, 8, Actions.NULL, StateNum.S_BOS2_RAISE2);
        addState(StateNum.S_BOS2_RAISE2, SpriteNames.BOS2, 12, 8, Actions.NULL, StateNum.S_BOS2_RAISE3);
        addState(StateNum.S_BOS2_RAISE3, SpriteNames.BOS2, 11, 8, Actions.NULL, StateNum.S_BOS2_RAISE4);
        addState(StateNum.S_BOS2_RAISE4, SpriteNames.BOS2, 10, 8, Actions.NULL, StateNum.S_BOS2_RAISE5);
        addState(StateNum.S_BOS2_RAISE5, SpriteNames.BOS2, 9, 8, Actions.NULL, StateNum.S_BOS2_RAISE6);
        addState(StateNum.S_BOS2_RAISE6, SpriteNames.BOS2, 8, 8, Actions.NULL, StateNum.S_BOS2_RAISE7);
        addState(StateNum.S_BOS2_RAISE7, SpriteNames.BOS2, 7, 8, Actions.NULL, StateNum.S_BOS2_RUN1);

        // MobjInfo for MT_BRUISER (Baron of Hell)
        MobjInfoDef baronInfo = new MobjInfoDef(
                "MT_BRUISER", 3003, StateNum.S_BOSS_STND, 1000, StateNum.S_BOSS_RUN1,
                SoundKey.SFX_BRSSIT, // seeSound
                8, // reactiontime
                SoundKey.SFX_FIRSHT, // attackSound
                StateNum.S_BOSS_PAIN, 50, // painstate, painchance
                SoundKey.SFX_PLPAIN, // painSound
                StateNum.S_BOSS_ATK1, // melee
                StateNum.S_BOSS_ATK1, StateNum.S_BOSS_DIE1, StateNum.S_NULL, // missile, death, xdeath
                SoundKey.SFX_BRSSIT, // deathSound
                8, 24.0, 64.0, 1000, 10, // speed, radius, height, mass, damage
                SoundKey.SFX_NONE, // activeSound
                MobjFlags.MF_SOLID | MobjFlags.MF_SHOOTABLE | MobjFlags.MF_COUNTKILL,
                StateNum.S_BOSS_RAISE1
        );
        mobjInfos.put(MobjType.MT_BRUISER, baronInfo);
        doomedNumToMobjType.put(3003, MobjType.MT_BRUISER);

        // MobjInfo for MT_KNIGHT (Hell Knight)
        MobjInfoDef knightInfo = new MobjInfoDef(
                "MT_KNIGHT", 69, StateNum.S_BOS2_STND, 500, StateNum.S_BOS2_RUN1,
                SoundKey.SFX_KNTSIT, // seeSound
                8, // reactiontime
                SoundKey.SFX_FIRSHT, // attackSound
                StateNum.S_BOS2_PAIN, 50, // painstate, painchance
                SoundKey.SFX_PLPAIN, // painSound
                StateNum.S_BOS2_ATK1, // melee
                StateNum.S_BOS2_ATK1, StateNum.S_BOS2_DIE1, StateNum.S_NULL, // missile, death, xdeath
                SoundKey.SFX_KNTSIT, // deathSound
                8, 24.0, 64.0, 500, 8, // speed, radius, height, mass, damage
                SoundKey.SFX_NONE, // activeSound
                MobjFlags.MF_SOLID | MobjFlags.MF_SHOOTABLE | MobjFlags.MF_COUNTKILL,
                StateNum.S_BOS2_RAISE1
        );
        mobjInfos.put(MobjType.MT_KNIGHT, knightInfo);
        doomedNumToMobjType.put(69, MobjType.MT_KNIGHT);
    }

    private void populateLostSoul() {
        // Lost Soul states (floating, charging enemy)
        addState(StateNum.S_SKULL_STND, SpriteNames.SKUL, 0 | 0x8000, 10, Actions.A_LOOK, StateNum.S_SKULL_STND2);
        addState(StateNum.S_SKULL_STND2, SpriteNames.SKUL, 1 | 0x8000, 10, Actions.A_LOOK, StateNum.S_SKULL_STND);
        addState(StateNum.S_SKULL_RUN1, SpriteNames.SKUL, 0 | 0x8000, 6, Actions.A_CHASE, StateNum.S_SKULL_RUN2);
        addState(StateNum.S_SKULL_RUN2, SpriteNames.SKUL, 1 | 0x8000, 6, Actions.A_CHASE, StateNum.S_SKULL_RUN1);

        // Attack states (charging)
        addState(StateNum.S_SKULL_ATK1, SpriteNames.SKUL, 2 | 0x8000, 4, Actions.A_FACE_TARGET, StateNum.S_SKULL_ATK2);
        addState(StateNum.S_SKULL_ATK2, SpriteNames.SKUL, 3 | 0x8000, 2, Actions.A_SARG_ATTACK, StateNum.S_SKULL_ATK3);
        addState(StateNum.S_SKULL_ATK3, SpriteNames.SKUL, 2 | 0x8000, 2, Actions.NULL, StateNum.S_SKULL_ATK4);
        addState(StateNum.S_SKULL_ATK4, SpriteNames.SKUL, 3 | 0x8000, 2, Actions.NULL, StateNum.S_SKULL_RUN1);

        // Pain states
        addState(StateNum.S_SKULL_PAIN, SpriteNames.SKUL, 4 | 0x8000, 3, Actions.NULL, StateNum.S_SKULL_PAIN2);
        addState(StateNum.S_SKULL_PAIN2, SpriteNames.SKUL, 4 | 0x8000, 3, Actions.A_PAIN, StateNum.S_SKULL_RUN1);

        // Death states
        addState(StateNum.S_SKULL_DIE1, SpriteNames.SKUL, 5 | 0x8000, 6, Actions.NULL, StateNum.S_SKULL_DIE2);
        addState(StateNum.S_SKULL_DIE2, SpriteNames.SKUL, 6 | 0x8000, 6, Actions.A_SCREAM, StateNum.S_SKULL_DIE3);
        addState(StateNum.S_SKULL_DIE3, SpriteNames.SKUL, 7 | 0x8000, 6, Actions.NULL, StateNum.S_SKULL_DIE4);
        addState(StateNum.S_SKULL_DIE4, SpriteNames.SKUL, 8 | 0x8000, 6, Actions.A_FALL, StateNum.S_SKULL_DIE5);
        addState(StateNum.S_SKULL_DIE5, SpriteNames.SKUL, 9, 6, Actions.NULL, StateNum.S_SKULL_DIE6);
        addState(StateNum.S_SKULL_DIE6, SpriteNames.SKUL, 10, -1, Actions.NULL, StateNum.S_SKULL_DIE6);

        // MobjInfo for MT_SKULL (Lost Soul)
        MobjInfoDef skullInfo = new MobjInfoDef(
                "MT_SKULL", 3006, StateNum.S_SKULL_STND, 100, StateNum.S_SKULL_RUN1,
                SoundKey.SFX_NONE, // seeSound (silent)
                8, // reactiontime
                SoundKey.SFX_SKLATK, // attackSound
                StateNum.S_SKULL_PAIN, 256, // painstate, painchance
                SoundKey.SFX_PLPAIN, // painSound
                StateNum.S_SKULL_ATK1, // melee (charge attack)
                StateNum.S_NULL, StateNum.S_SKULL_DIE1, StateNum.S_NULL, // no missile, death, xdeath
                SoundKey.SFX_SKLATK, // deathSound
                8, 16.0, 56.0, 50, 3, // speed, radius, height, mass, damage
                SoundKey.SFX_NONE, // activeSound
                MobjFlags.MF_SOLID | MobjFlags.MF_SHOOTABLE | MobjFlags.MF_COUNTKILL | MobjFlags.MF_NOGRAVITY | MobjFlags.MF_FLOAT,
                StateNum.S_NULL // no raise (lost souls don't resurrect)
        );
        mobjInfos.put(MobjType.MT_SKULL, skullInfo);
        doomedNumToMobjType.put(3006, MobjType.MT_SKULL);
    }

    private void populateChaingunner() {
        // Chaingunner states (heavy weapons guy)
        addState(StateNum.S_CPOS_STND, SpriteNames.CPOS, 0, 10, Actions.A_LOOK, StateNum.S_CPOS_STND2);
        addState(StateNum.S_CPOS_STND2, SpriteNames.CPOS, 1, 10, Actions.A_LOOK, StateNum.S_CPOS_STND);
        addState(StateNum.S_CPOS_RUN1, SpriteNames.CPOS, 0, 3, Actions.A_CHASE, StateNum.S_CPOS_RUN2);
        addState(StateNum.S_CPOS_RUN2, SpriteNames.CPOS, 0, 3, Actions.A_CHASE, StateNum.S_CPOS_RUN3);
        addState(StateNum.S_CPOS_RUN3, SpriteNames.CPOS, 1, 3, Actions.A_CHASE, StateNum.S_CPOS_RUN4);
        addState(StateNum.S_CPOS_RUN4, SpriteNames.CPOS, 1, 3, Actions.A_CHASE, StateNum.S_CPOS_RUN5);
        addState(StateNum.S_CPOS_RUN5, SpriteNames.CPOS, 2, 3, Actions.A_CHASE, StateNum.S_CPOS_RUN6);
        addState(StateNum.S_CPOS_RUN6, SpriteNames.CPOS, 2, 3, Actions.A_CHASE, StateNum.S_CPOS_RUN7);
        addState(StateNum.S_CPOS_RUN7, SpriteNames.CPOS, 3, 3, Actions.A_CHASE, StateNum.S_CPOS_RUN8);
        addState(StateNum.S_CPOS_RUN8, SpriteNames.CPOS, 3, 3, Actions.A_CHASE, StateNum.S_CPOS_RUN1);

        // Attack states (chaingun burst)
        addState(StateNum.S_CPOS_ATK1, SpriteNames.CPOS, 4, 10, Actions.A_FACE_TARGET, StateNum.S_CPOS_ATK2);
        addState(StateNum.S_CPOS_ATK2, SpriteNames.CPOS, 5 | 0x8000, 4, Actions.A_POS_ATTACK, StateNum.S_CPOS_ATK3);
        addState(StateNum.S_CPOS_ATK3, SpriteNames.CPOS, 4, 4, Actions.A_POS_ATTACK, StateNum.S_CPOS_ATK4);
        addState(StateNum.S_CPOS_ATK4, SpriteNames.CPOS, 5 | 0x8000, 1, Actions.NULL, StateNum.S_CPOS_RUN1);

        // Pain states
        addState(StateNum.S_CPOS_PAIN, SpriteNames.CPOS, 6, 3, Actions.NULL, StateNum.S_CPOS_PAIN2);
        addState(StateNum.S_CPOS_PAIN2, SpriteNames.CPOS, 6, 3, Actions.A_PAIN, StateNum.S_CPOS_RUN1);

        // Death states
        addState(StateNum.S_CPOS_DIE1, SpriteNames.CPOS, 7, 5, Actions.NULL, StateNum.S_CPOS_DIE2);
        addState(StateNum.S_CPOS_DIE2, SpriteNames.CPOS, 8, 5, Actions.A_SCREAM, StateNum.S_CPOS_DIE3);
        addState(StateNum.S_CPOS_DIE3, SpriteNames.CPOS, 9, 5, Actions.A_FALL, StateNum.S_CPOS_DIE4);
        addState(StateNum.S_CPOS_DIE4, SpriteNames.CPOS, 10, 5, Actions.NULL, StateNum.S_CPOS_DIE5);
        addState(StateNum.S_CPOS_DIE5, SpriteNames.CPOS, 11, 5, Actions.NULL, StateNum.S_CPOS_DIE6);
        addState(StateNum.S_CPOS_DIE6, SpriteNames.CPOS, 12, 5, Actions.NULL, StateNum.S_CPOS_DIE7);
        addState(StateNum.S_CPOS_DIE7, SpriteNames.CPOS, 13, -1, Actions.NULL, StateNum.S_CPOS_DIE7);

        // XDeath states
        addState(StateNum.S_CPOS_XDIE1, SpriteNames.CPOS, 14, 5, Actions.NULL, StateNum.S_CPOS_XDIE2);
        addState(StateNum.S_CPOS_XDIE2, SpriteNames.CPOS, 15, 5, Actions.A_XSCREAM, StateNum.S_CPOS_XDIE3);
        addState(StateNum.S_CPOS_XDIE3, SpriteNames.CPOS, 16, 5, Actions.A_FALL, StateNum.S_CPOS_XDIE4);
        addState(StateNum.S_CPOS_XDIE4, SpriteNames.CPOS, 17, 5, Actions.NULL, StateNum.S_CPOS_XDIE5);
        addState(StateNum.S_CPOS_XDIE5, SpriteNames.CPOS, 18, 5, Actions.NULL, StateNum.S_CPOS_XDIE6);
        addState(StateNum.S_CPOS_XDIE6, SpriteNames.CPOS, 19, -1, Actions.NULL, StateNum.S_CPOS_XDIE6);

        // Raise states
        addState(StateNum.S_CPOS_RAISE1, SpriteNames.CPOS, 13, 5, Actions.NULL, StateNum.S_CPOS_RAISE2);
        addState(StateNum.S_CPOS_RAISE2, SpriteNames.CPOS, 12, 5, Actions.NULL, StateNum.S_CPOS_RAISE3);
        addState(StateNum.S_CPOS_RAISE3, SpriteNames.CPOS, 11, 5, Actions.NULL, StateNum.S_CPOS_RAISE4);
        addState(StateNum.S_CPOS_RAISE4, SpriteNames.CPOS, 10, 5, Actions.NULL, StateNum.S_CPOS_RUN1);

        // MobjInfo for MT_CHAINGUY (Chaingunner)
        MobjInfoDef chainguyInfo = new MobjInfoDef(
                "MT_CHAINGUY", 65, StateNum.S_CPOS_STND, 70, StateNum.S_CPOS_RUN1,
                SoundKey.SFX_POSIT1, // seeSound
                8, // reactiontime
                SoundKey.SFX_PISTOL, // attackSound
                StateNum.S_CPOS_PAIN, 170, // painstate, painchance
                SoundKey.SFX_PLPAIN, // painSound
                StateNum.S_NULL, // no melee
                StateNum.S_CPOS_ATK1, StateNum.S_CPOS_DIE1, StateNum.S_CPOS_XDIE1, // missile, death, xdeath
                SoundKey.SFX_PODTH2, // deathSound
                8, 20.0, 56.0, 100, 0, // speed, radius, height, mass, damage
                SoundKey.SFX_POSIT2, // activeSound
                MobjFlags.MF_SOLID | MobjFlags.MF_SHOOTABLE | MobjFlags.MF_COUNTKILL,
                StateNum.S_CPOS_RAISE1
        );
        mobjInfos.put(MobjType.MT_CHAINGUY, chainguyInfo);
        doomedNumToMobjType.put(65, MobjType.MT_CHAINGUY);
    }

    private void populateSpectre() {
        // Spectre uses same states as demon, but is partially invisible
        // This is handled by rendering flags, not different states

        // MobjInfo for MT_SHADOWS (Spectre) - invisible demon
        MobjInfoDef spectreInfo = new MobjInfoDef(
                "MT_SHADOWS", 58, StateNum.S_SARG_STND, 150, StateNum.S_SARG_RUN1,
                SoundKey.SFX_SGTSIT, // seeSound
                8, // reactiontime
                SoundKey.SFX_SGTATK, // attackSound
                StateNum.S_SARG_PAIN, 180, // painstate, painchance
                SoundKey.SFX_PLPAIN, // painSound
                StateNum.S_SARG_ATK1, // melee only
                StateNum.S_NULL, StateNum.S_SARG_DIE1, StateNum.S_NULL, // no missile, death, xdeath
                SoundKey.SFX_PODTH3, // deathSound
                10, 30.0, 56.0, 400, 4, // speed, radius, height, mass, damage
                SoundKey.SFX_NONE, // activeSound
                MobjFlags.MF_SOLID | MobjFlags.MF_SHOOTABLE | MobjFlags.MF_COUNTKILL | MobjFlags.MF_SHADOW,
                StateNum.S_SARG_RAISE1
        );
        mobjInfos.put(MobjType.MT_SHADOWS, spectreInfo);
        doomedNumToMobjType.put(58, MobjType.MT_SHADOWS);
    }

    private void populateBigEnemies() {
        // Cyberdemon states (boss enemy)
        addState(StateNum.S_CYBER_STND, SpriteNames.CYBR, 0, 10, Actions.A_LOOK, StateNum.S_CYBER_STND2);
        addState(StateNum.S_CYBER_STND2, SpriteNames.CYBR, 1, 10, Actions.A_LOOK, StateNum.S_CYBER_STND);
        addState(StateNum.S_CYBER_RUN1, SpriteNames.CYBR, 0, 3, Actions.A_CHASE, StateNum.S_CYBER_RUN2);
        addState(StateNum.S_CYBER_RUN2, SpriteNames.CYBR, 0, 3, Actions.A_CHASE, StateNum.S_CYBER_RUN3);
        addState(StateNum.S_CYBER_RUN3, SpriteNames.CYBR, 1, 3, Actions.A_CHASE, StateNum.S_CYBER_RUN4);
        addState(StateNum.S_CYBER_RUN4, SpriteNames.CYBR, 1, 3, Actions.A_CHASE, StateNum.S_CYBER_RUN5);
        addState(StateNum.S_CYBER_RUN5, SpriteNames.CYBR, 2, 3, Actions.A_CHASE, StateNum.S_CYBER_RUN6);
        addState(StateNum.S_CYBER_RUN6, SpriteNames.CYBR, 2, 3, Actions.A_CHASE, StateNum.S_CYBER_RUN7);
        addState(StateNum.S_CYBER_RUN7, SpriteNames.CYBR, 3, 3, Actions.A_CHASE, StateNum.S_CYBER_RUN8);
        addState(StateNum.S_CYBER_RUN8, SpriteNames.CYBR, 3, 3, Actions.A_CHASE, StateNum.S_CYBER_RUN1);

        // Attack states (rocket launcher)
        addState(StateNum.S_CYBER_ATK1, SpriteNames.CYBR, 4, 6, Actions.A_FACE_TARGET, StateNum.S_CYBER_ATK2);
        addState(StateNum.S_CYBER_ATK2, SpriteNames.CYBR, 5, 12, Actions.A_TROOP_MISSILE, StateNum.S_CYBER_ATK3);
        addState(StateNum.S_CYBER_ATK3, SpriteNames.CYBR, 4, 12, Actions.A_FACE_TARGET, StateNum.S_CYBER_ATK4);
        addState(StateNum.S_CYBER_ATK4, SpriteNames.CYBR, 5, 12, Actions.A_TROOP_MISSILE, StateNum.S_CYBER_ATK5);
        addState(StateNum.S_CYBER_ATK5, SpriteNames.CYBR, 4, 12, Actions.A_FACE_TARGET, StateNum.S_CYBER_ATK6);
        addState(StateNum.S_CYBER_ATK6, SpriteNames.CYBR, 5, 12, Actions.A_TROOP_MISSILE, StateNum.S_CYBER_RUN1);

        // Pain state
        addState(StateNum.S_CYBER_PAIN, SpriteNames.CYBR, 6, 10, Actions.A_PAIN, StateNum.S_CYBER_RUN1);

        // Death states
        addState(StateNum.S_CYBER_DIE1, SpriteNames.CYBR, 7, 10, Actions.NULL, StateNum.S_CYBER_DIE2);
        addState(StateNum.S_CYBER_DIE2, SpriteNames.CYBR, 8, 10, Actions.A_SCREAM, StateNum.S_CYBER_DIE3);
        addState(StateNum.S_CYBER_DIE3, SpriteNames.CYBR, 9, 10, Actions.NULL, StateNum.S_CYBER_DIE4);
        addState(StateNum.S_CYBER_DIE4, SpriteNames.CYBR, 10, 10, Actions.NULL, StateNum.S_CYBER_DIE5);
        addState(StateNum.S_CYBER_DIE5, SpriteNames.CYBR, 11, 10, Actions.NULL, StateNum.S_CYBER_DIE6);
        addState(StateNum.S_CYBER_DIE6, SpriteNames.CYBR, 12, 10, Actions.A_FALL, StateNum.S_CYBER_DIE7);
        addState(StateNum.S_CYBER_DIE7, SpriteNames.CYBR, 13, 10, Actions.NULL, StateNum.S_CYBER_DIE8);
        addState(StateNum.S_CYBER_DIE8, SpriteNames.CYBR, 14, 10, Actions.NULL, StateNum.S_CYBER_DIE9);
        addState(StateNum.S_CYBER_DIE9, SpriteNames.CYBR, 15, 10, Actions.NULL, StateNum.S_CYBER_DIE10);
        addState(StateNum.S_CYBER_DIE10, SpriteNames.CYBR, 16, -1, Actions.NULL, StateNum.S_CYBER_DIE10);

        // Spider Mastermind states
        addState(StateNum.S_SPID_STND, SpriteNames.SPID, 0, 10, Actions.A_LOOK, StateNum.S_SPID_STND2);
        addState(StateNum.S_SPID_STND2, SpriteNames.SPID, 1, 10, Actions.A_LOOK, StateNum.S_SPID_STND);
        addState(StateNum.S_SPID_RUN1, SpriteNames.SPID, 0, 3, Actions.A_CHASE, StateNum.S_SPID_RUN2);
        addState(StateNum.S_SPID_RUN2, SpriteNames.SPID, 0, 3, Actions.A_CHASE, StateNum.S_SPID_RUN3);
        addState(StateNum.S_SPID_RUN3, SpriteNames.SPID, 1, 3, Actions.A_CHASE, StateNum.S_SPID_RUN4);
        addState(StateNum.S_SPID_RUN4, SpriteNames.SPID, 1, 3, Actions.A_CHASE, StateNum.S_SPID_RUN5);
        addState(StateNum.S_SPID_RUN5, SpriteNames.SPID, 2, 3, Actions.A_CHASE, StateNum.S_SPID_RUN6);
        addState(StateNum.S_SPID_RUN6, SpriteNames.SPID, 2, 3, Actions.A_CHASE, StateNum.S_SPID_RUN7);
        addState(StateNum.S_SPID_RUN7, SpriteNames.SPID, 3, 3, Actions.A_CHASE, StateNum.S_SPID_RUN8);
        addState(StateNum.S_SPID_RUN8, SpriteNames.SPID, 3, 3, Actions.A_CHASE, StateNum.S_SPID_RUN9);
        addState(StateNum.S_SPID_RUN9, SpriteNames.SPID, 4, 3, Actions.A_CHASE, StateNum.S_SPID_RUN10);
        addState(StateNum.S_SPID_RUN10, SpriteNames.SPID, 4, 3, Actions.A_CHASE, StateNum.S_SPID_RUN11);
        addState(StateNum.S_SPID_RUN11, SpriteNames.SPID, 5, 3, Actions.A_CHASE, StateNum.S_SPID_RUN12);
        addState(StateNum.S_SPID_RUN12, SpriteNames.SPID, 5, 3, Actions.A_CHASE, StateNum.S_SPID_RUN1);

        // Attack states (chaingun)
        addState(StateNum.S_SPID_ATK1, SpriteNames.SPID, 6, 20, Actions.A_FACE_TARGET, StateNum.S_SPID_ATK2);
        addState(StateNum.S_SPID_ATK2, SpriteNames.SPID, 7 | 0x8000, 4, Actions.A_POS_ATTACK, StateNum.S_SPID_ATK3);
        addState(StateNum.S_SPID_ATK3, SpriteNames.SPID, 7 | 0x8000, 4, Actions.A_POS_ATTACK, StateNum.S_SPID_ATK4);
        addState(StateNum.S_SPID_ATK4, SpriteNames.SPID, 7 | 0x8000, 1, Actions.NULL, StateNum.S_SPID_RUN1);

        // Pain states
        addState(StateNum.S_SPID_PAIN, SpriteNames.SPID, 8, 3, Actions.NULL, StateNum.S_SPID_PAIN2);
        addState(StateNum.S_SPID_PAIN2, SpriteNames.SPID, 8, 3, Actions.A_PAIN, StateNum.S_SPID_RUN1);

        // Death states
        addState(StateNum.S_SPID_DIE1, SpriteNames.SPID, 9, 20, Actions.A_SCREAM, StateNum.S_SPID_DIE2);
        addState(StateNum.S_SPID_DIE2, SpriteNames.SPID, 10, 10, Actions.A_FALL, StateNum.S_SPID_DIE3);
        addState(StateNum.S_SPID_DIE3, SpriteNames.SPID, 11, 10, Actions.NULL, StateNum.S_SPID_DIE4);
        addState(StateNum.S_SPID_DIE4, SpriteNames.SPID, 12, 10, Actions.NULL, StateNum.S_SPID_DIE5);
        addState(StateNum.S_SPID_DIE5, SpriteNames.SPID, 13, 10, Actions.NULL, StateNum.S_SPID_DIE6);
        addState(StateNum.S_SPID_DIE6, SpriteNames.SPID, 14, 10, Actions.NULL, StateNum.S_SPID_DIE7);
        addState(StateNum.S_SPID_DIE7, SpriteNames.SPID, 15, 10, Actions.NULL, StateNum.S_SPID_DIE8);
        addState(StateNum.S_SPID_DIE8, SpriteNames.SPID, 16, 10, Actions.NULL, StateNum.S_SPID_DIE9);
        addState(StateNum.S_SPID_DIE9, SpriteNames.SPID, 17, 10, Actions.NULL, StateNum.S_SPID_DIE10);
        addState(StateNum.S_SPID_DIE10, SpriteNames.SPID, 18, 30, Actions.NULL, StateNum.S_SPID_DIE11);
        addState(StateNum.S_SPID_DIE11, SpriteNames.SPID, 19, -1, Actions.NULL, StateNum.S_SPID_DIE11);

        // MobjInfo for MT_CYBORG (Cyberdemon)
        MobjInfoDef cyberInfo = new MobjInfoDef(
                "MT_CYBORG", 16, StateNum.S_CYBER_STND, 4000, StateNum.S_CYBER_RUN1,
                SoundKey.SFX_CYBSIT, // seeSound
                8, // reactiontime
                SoundKey.SFX_RLAUNC, // attackSound
                StateNum.S_CYBER_PAIN, 20, // painstate, painchance
                SoundKey.SFX_PLPAIN, // painSound
                StateNum.S_NULL, // no melee
                StateNum.S_CYBER_ATK1, StateNum.S_CYBER_DIE1, StateNum.S_NULL, // missile, death, xdeath
                SoundKey.SFX_CYBSIT, // deathSound
                16, 40.0, 110.0, 1000, 20, // speed, radius, height, mass, damage
                SoundKey.SFX_NONE, // activeSound
                MobjFlags.MF_SOLID | MobjFlags.MF_SHOOTABLE | MobjFlags.MF_COUNTKILL,
                StateNum.S_NULL // no raise (boss doesn't resurrect)
        );
        mobjInfos.put(MobjType.MT_CYBORG, cyberInfo);
        doomedNumToMobjType.put(16, MobjType.MT_CYBORG);

        // MobjInfo for MT_SPIDER (Spider Mastermind)
        MobjInfoDef spiderInfo = new MobjInfoDef(
                "MT_SPIDER", 7, StateNum.S_SPID_STND, 3000, StateNum.S_SPID_RUN1,
                SoundKey.SFX_SPISIT, // seeSound
                8, // reactiontime
                SoundKey.SFX_PISTOL, // attackSound
                StateNum.S_SPID_PAIN, 40, // painstate, painchance
                SoundKey.SFX_PLPAIN, // painSound
                StateNum.S_NULL, // no melee
                StateNum.S_SPID_ATK1, StateNum.S_SPID_DIE1, StateNum.S_NULL, // missile, death, xdeath
                SoundKey.SFX_SPISIT, // deathSound
                12, 128.0, 100.0, 1000, 0, // speed, radius, height, mass, damage
                SoundKey.SFX_NONE, // activeSound
                MobjFlags.MF_SOLID | MobjFlags.MF_SHOOTABLE | MobjFlags.MF_COUNTKILL,
                StateNum.S_NULL // no raise (boss doesn't resurrect)
        );
        mobjInfos.put(MobjType.MT_SPIDER, spiderInfo);
        doomedNumToMobjType.put(7, MobjType.MT_SPIDER);
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
        addState(StateNum.S_PLAY, SpriteNames.PLAY, 0, -1, Actions.NULL, StateNum.S_PLAY); // Idle
        // Add S_PLAY_RUN1 etc. if needed for player sprite animation,
        // but player movement is usually directly controlled, not by state tics.
        // Player death states can be added similarly to monsters if player character shows death anim.
        // ... (add S_PLAY_PAIN, S_PLAY_DIE1 etc. if player visuals use them)

        MobjInfoDef playerInfo = new MobjInfoDef(
                "MT_PLAYER", TYPE_PLAYER_START_1,
                StateNum.S_PLAY, 100, StateNum.S_PLAY_RUN1, // spawnstate, spawnhealth, seestate
                SoundKey.SFX_NONE, // seeSound
                0, // reactiontime
                SoundKey.SFX_NONE, // attackSound
                StateNum.S_PLAY_PAIN, 255, // painstate, painchance
                SoundKey.SFX_PLPAIN, // painSound
                StateNum.S_NULL,      // meleestate (player uses weapons differently)
                StateNum.S_PLAY_ATK1, // missilestate (player uses weapons differently)
                StateNum.S_PLAY_DIE1, StateNum.S_PLAY_XDIE1, // deathstate, xdeathstate
                SoundKey.SFX_PLDETH, // deathSound
                0, // speed (player handles its own movement speed)
                16.0, // radius (Doom default)
                56.0, // height (Doom default total height)
                100,  // mass
                0,    // damage (player deals damage via weapons)
                SoundKey.SFX_NONE, // activeSound
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
            MobjAction currentAtkAction = (atkActions != null && i < atkActions.length && atkActions[i] != null) ? atkActions[i] : Actions.A_FACE_TARGET;
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
            StateNum nextDie = isLastFrame ? currentDie : StateNum.values()[currentDie.ordinal() + 1]; // Stay in final death state instead of S_NULL
            MobjAction currentDieAction = (dieActions != null && i < dieActions.length && dieActions[i] != null) ? dieActions[i] : Actions.NULL;
            int tics = isLastFrame ? -1 : dieTics[i]; // Final death frame should be permanent
            addState(currentDie, spriteName, dieFrames[i], tics, currentDieAction, nextDie);
            if (currentDie == dieCycleEnd) break;
            if (i < dieFrames.length - 1) currentDie = nextDie;
        }

        // Xtreme Die cycle (if applicable)
        if (xdie1 != null) {
            StateNum currentXDie = xdie1;
            for (int i = 0; i < xdieFrames.length; i++) {
                boolean isLastFrame = (i == xdieFrames.length - 1 || currentXDie == xdieCycleEnd);
                StateNum nextXDie = isLastFrame ? currentXDie : StateNum.values()[currentXDie.ordinal() + 1]; // Stay in final death state instead of S_NULL
                MobjAction currentXDieAction = (xdieActions != null && i < xdieActions.length && xdieActions[i] != null) ? xdieActions[i] : Actions.NULL;
                int tics = isLastFrame ? -1 : xdieTics[i]; // Final death frame should be permanent
                addState(currentXDie, spriteName, xdieFrames[i], tics, currentXDieAction, nextXDie);
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
                addState(currentRaise, spriteName, raiseFrames[i], raiseTics[i], Actions.NULL, nextRaise);
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
        addState(StateNum.S_POSS_STND, SpriteNames.POSS, 0, 10, Actions.A_LOOK, StateNum.S_POSS_STND2);
        addState(StateNum.S_POSS_STND2, SpriteNames.POSS, 1, 10, Actions.A_LOOK, StateNum.S_POSS_STND);

        // Run: 8 frames (0,0,1,1,2,2,3,3), 4 tics each
        addState(StateNum.S_POSS_RUN1, SpriteNames.POSS, 0, 4, Actions.A_CHASE, StateNum.S_POSS_RUN2);
        addState(StateNum.S_POSS_RUN2, SpriteNames.POSS, 0, 4, Actions.A_CHASE, StateNum.S_POSS_RUN3);
        addState(StateNum.S_POSS_RUN3, SpriteNames.POSS, 1, 4, Actions.A_CHASE, StateNum.S_POSS_RUN4);
        addState(StateNum.S_POSS_RUN4, SpriteNames.POSS, 1, 4, Actions.A_CHASE, StateNum.S_POSS_RUN5);
        addState(StateNum.S_POSS_RUN5, SpriteNames.POSS, 2, 4, Actions.A_CHASE, StateNum.S_POSS_RUN6);
        addState(StateNum.S_POSS_RUN6, SpriteNames.POSS, 2, 4, Actions.A_CHASE, StateNum.S_POSS_RUN7);
        addState(StateNum.S_POSS_RUN7, SpriteNames.POSS, 3, 4, Actions.A_CHASE, StateNum.S_POSS_RUN8);
        addState(StateNum.S_POSS_RUN8, SpriteNames.POSS, 3, 4, Actions.A_CHASE, StateNum.S_POSS_RUN1);

        // Attack: 3 frames (4,5,4), tics (10,8,8)
        addState(StateNum.S_POSS_ATK1, SpriteNames.POSS, 4, 10, Actions.A_FACE_TARGET, StateNum.S_POSS_ATK2);
        addState(StateNum.S_POSS_ATK2, SpriteNames.POSS, 5, 8, Actions.A_POS_ATTACK, StateNum.S_POSS_ATK3);
        addState(StateNum.S_POSS_ATK3, SpriteNames.POSS, 4, 8, Actions.NULL, StateNum.S_POSS_RUN1);

        // Pain: 2 frames (6,6), tics (3,3)
        addState(StateNum.S_POSS_PAIN, SpriteNames.POSS, 6, 3, Actions.NULL, StateNum.S_POSS_PAIN2);
        addState(StateNum.S_POSS_PAIN2, SpriteNames.POSS, 6, 3, Actions.A_PAIN, StateNum.S_POSS_RUN1);

        // Die: 5 frames (7,8,9,10,11), tics (5,5,5,5,-1) - proper death sequence
        addState(StateNum.S_POSS_DIE1, SpriteNames.POSS, 7, 5, Actions.NULL, StateNum.S_POSS_DIE2);
        addState(StateNum.S_POSS_DIE2, SpriteNames.POSS, 8, 5, Actions.A_SCREAM, StateNum.S_POSS_DIE3);
        addState(StateNum.S_POSS_DIE3, SpriteNames.POSS, 9, 5, Actions.A_FALL, StateNum.S_POSS_DIE4);
        addState(StateNum.S_POSS_DIE4, SpriteNames.POSS, 10, 5, Actions.NULL, StateNum.S_POSS_DIE5);
        addState(StateNum.S_POSS_DIE5, SpriteNames.POSS, 11, -1, Actions.NULL, StateNum.S_POSS_DIE5); // Final dead body frame L

        // XDeath: 9 frames (12 to 20), tics (5...5, -1)
        addState(StateNum.S_POSS_XDIE1, SpriteNames.POSS, 12, 5, Actions.NULL, StateNum.S_POSS_XDIE2);
        addState(StateNum.S_POSS_XDIE2, SpriteNames.POSS, 13, 5, Actions.A_XSCREAM, StateNum.S_POSS_XDIE3);
        addState(StateNum.S_POSS_XDIE3, SpriteNames.POSS, 14, 5, Actions.A_FALL, StateNum.S_POSS_XDIE4);
        addState(StateNum.S_POSS_XDIE4, SpriteNames.POSS, 15, 5, Actions.NULL, StateNum.S_POSS_XDIE5);
        addState(StateNum.S_POSS_XDIE5, SpriteNames.POSS, 16, 5, Actions.NULL, StateNum.S_POSS_XDIE6);
        addState(StateNum.S_POSS_XDIE6, SpriteNames.POSS, 17, 5, Actions.NULL, StateNum.S_POSS_XDIE7);
        addState(StateNum.S_POSS_XDIE7, SpriteNames.POSS, 18, 5, Actions.NULL, StateNum.S_POSS_XDIE8);
        addState(StateNum.S_POSS_XDIE8, SpriteNames.POSS, 19, 5, Actions.NULL, StateNum.S_POSS_XDIE9);
        addState(StateNum.S_POSS_XDIE9, SpriteNames.POSS, 20, -1, Actions.NULL, StateNum.S_POSS_XDIE9);

        // Raise: 4 frames (10,9,8,7), tics (5,5,5,5)
        addState(StateNum.S_POSS_RAISE1, SpriteNames.POSS, 10, 5, Actions.NULL, StateNum.S_POSS_RAISE2);
        addState(StateNum.S_POSS_RAISE2, SpriteNames.POSS, 9, 5, Actions.NULL, StateNum.S_POSS_RAISE3);
        addState(StateNum.S_POSS_RAISE3, SpriteNames.POSS, 8, 5, Actions.NULL, StateNum.S_POSS_RAISE4);
        addState(StateNum.S_POSS_RAISE4, SpriteNames.POSS, 7, 5, Actions.NULL, StateNum.S_POSS_RUN1);

        // MobjInfo for MT_POSSESSED (Zombieman)
        MobjInfoDef possessedInfo = new MobjInfoDef(
                "MT_POSSESSED", TYPE_POSSESSED, StateNum.S_POSS_STND, 20, StateNum.S_POSS_RUN1,
                SoundKey.SFX_POSIT1, // seeSound
                8, // reactiontime
                SoundKey.SFX_PISTOL, // attackSound
                StateNum.S_POSS_PAIN, 200, // painstate, painchance
                SoundKey.SFX_PLPAIN, // painSound (using player pain sound for now)
                StateNum.S_NULL, // Melee state S_NULL
                StateNum.S_POSS_ATK1, StateNum.S_POSS_DIE1, StateNum.S_POSS_XDIE1,
                SoundKey.SFX_PODTH1, // deathSound
                8, 20.0, 56.0, 100, 0, // speed, radius, height, mass, damage
                SoundKey.SFX_NONE, // activeSound
                MobjFlags.MF_SOLID | MobjFlags.MF_SHOOTABLE | MobjFlags.MF_COUNTKILL,
                StateNum.S_POSS_RAISE1
        );
        mobjInfos.put(MobjType.MT_POSSESSED, possessedInfo);
        doomedNumToMobjType.put(TYPE_POSSESSED, MobjType.MT_POSSESSED);
    }

    private void populateShotguy() { // Shotgun Guy (MT_SHOTGUY)
        // States: S_SPOS_STND (187) to S_SPOS_RAISE4 (213)
        // Sprite: SPR_SPOS (index 30)
        addState(StateNum.S_SPOS_STND, SpriteNames.SPOS, 0, 10, Actions.A_LOOK, StateNum.S_SPOS_STND2);
        addState(StateNum.S_SPOS_STND2, SpriteNames.SPOS, 1, 10, Actions.A_LOOK, StateNum.S_SPOS_STND);

        // Run: 8 frames (0,0,1,1,2,2,3,3), 3 tics each (faster than Zombieman)
        addState(StateNum.S_SPOS_RUN1, SpriteNames.SPOS, 0, 3, Actions.A_CHASE, StateNum.S_SPOS_RUN2);
        addState(StateNum.S_SPOS_RUN2, SpriteNames.SPOS, 0, 3, Actions.A_CHASE, StateNum.S_SPOS_RUN3);
        addState(StateNum.S_SPOS_RUN3, SpriteNames.SPOS, 1, 3, Actions.A_CHASE, StateNum.S_SPOS_RUN4);
        addState(StateNum.S_SPOS_RUN4, SpriteNames.SPOS, 1, 3, Actions.A_CHASE, StateNum.S_SPOS_RUN5);
        addState(StateNum.S_SPOS_RUN5, SpriteNames.SPOS, 2, 3, Actions.A_CHASE, StateNum.S_SPOS_RUN6);
        addState(StateNum.S_SPOS_RUN6, SpriteNames.SPOS, 2, 3, Actions.A_CHASE, StateNum.S_SPOS_RUN7);
        addState(StateNum.S_SPOS_RUN7, SpriteNames.SPOS, 3, 3, Actions.A_CHASE, StateNum.S_SPOS_RUN8);
        addState(StateNum.S_SPOS_RUN8, SpriteNames.SPOS, 3, 3, Actions.A_CHASE, StateNum.S_SPOS_RUN1);

        // Attack: 3 frames (4,5,4), tics (10,10,10) - shotgun attack pattern
        addState(StateNum.S_SPOS_ATK1, SpriteNames.SPOS, 4, 10, Actions.A_FACE_TARGET, StateNum.S_SPOS_ATK2);
        addState(StateNum.S_SPOS_ATK2, SpriteNames.SPOS, 5, 10, Actions.A_SPOS_ATTACK, StateNum.S_SPOS_ATK3);
        addState(StateNum.S_SPOS_ATK3, SpriteNames.SPOS, 4, 10, Actions.NULL, StateNum.S_SPOS_RUN1);

        // Pain: 2 frames (6,6), tics (8,8) - made longer to be more visible
        addState(StateNum.S_SPOS_PAIN, SpriteNames.SPOS, 6, 8, Actions.NULL, StateNum.S_SPOS_PAIN2);
        addState(StateNum.S_SPOS_PAIN2, SpriteNames.SPOS, 6, 8, Actions.A_PAIN, StateNum.S_SPOS_RUN1);

        // Die: 5 frames (7,8,9,10,11), tics (5,5,5,5,-1) - using more conservative frame indices
        addState(StateNum.S_SPOS_DIE1, SpriteNames.SPOS, 7, 5, Actions.NULL, StateNum.S_SPOS_DIE2);
        addState(StateNum.S_SPOS_DIE2, SpriteNames.SPOS, 8, 5, Actions.A_SCREAM, StateNum.S_SPOS_DIE3);
        addState(StateNum.S_SPOS_DIE3, SpriteNames.SPOS, 9, 5, Actions.A_FALL, StateNum.S_SPOS_DIE4);
        addState(StateNum.S_SPOS_DIE4, SpriteNames.SPOS, 10, 5, Actions.NULL, StateNum.S_SPOS_DIE5);
        addState(StateNum.S_SPOS_DIE5, SpriteNames.SPOS, 11, -1, Actions.NULL, StateNum.S_SPOS_DIE5); // Final dead body frame L

        // XDeath: 9 frames (12 to 20), tics (5...5, -1)
        addState(StateNum.S_SPOS_XDIE1, SpriteNames.SPOS, 12, 5, Actions.NULL, StateNum.S_SPOS_XDIE2);
        addState(StateNum.S_SPOS_XDIE2, SpriteNames.SPOS, 13, 5, Actions.A_XSCREAM, StateNum.S_SPOS_XDIE3);
        addState(StateNum.S_SPOS_XDIE3, SpriteNames.SPOS, 14, 5, Actions.A_FALL, StateNum.S_SPOS_XDIE4);
        addState(StateNum.S_SPOS_XDIE4, SpriteNames.SPOS, 15, 5, Actions.NULL, StateNum.S_SPOS_XDIE5);
        addState(StateNum.S_SPOS_XDIE5, SpriteNames.SPOS, 16, 5, Actions.NULL, StateNum.S_SPOS_XDIE6);
        addState(StateNum.S_SPOS_XDIE6, SpriteNames.SPOS, 17, 5, Actions.NULL, StateNum.S_SPOS_XDIE7);
        addState(StateNum.S_SPOS_XDIE7, SpriteNames.SPOS, 18, 5, Actions.NULL, StateNum.S_SPOS_XDIE8);
        addState(StateNum.S_SPOS_XDIE8, SpriteNames.SPOS, 19, 5, Actions.NULL, StateNum.S_SPOS_XDIE9);
        addState(StateNum.S_SPOS_XDIE9, SpriteNames.SPOS, 20, -1, Actions.NULL, StateNum.S_SPOS_XDIE9);

        // Raise: 4 frames (10,9,8,7), tics (5,5,5,5)
        addState(StateNum.S_SPOS_RAISE1, SpriteNames.SPOS, 10, 5, Actions.NULL, StateNum.S_SPOS_RAISE2);
        addState(StateNum.S_SPOS_RAISE2, SpriteNames.SPOS, 9, 5, Actions.NULL, StateNum.S_SPOS_RAISE3);
        addState(StateNum.S_SPOS_RAISE3, SpriteNames.SPOS, 8, 5, Actions.NULL, StateNum.S_SPOS_RAISE4);
        addState(StateNum.S_SPOS_RAISE4, SpriteNames.SPOS, 7, 5, Actions.NULL, StateNum.S_SPOS_RUN1);

        // MobjInfo for MT_SHOTGUY (Shotgun Guy)
        MobjInfoDef shotguyInfo = new MobjInfoDef(
                "MT_SHOTGUY", TYPE_SHOTGUY, StateNum.S_SPOS_STND, 30, StateNum.S_SPOS_RUN1,
                SoundKey.SFX_POSIT2, // seeSound
                8, // reactiontime
                SoundKey.SFX_SHOTGN, // attackSound
                StateNum.S_SPOS_PAIN, 240, // painstate, painchance (increased from 170 to make more likely)
                SoundKey.SFX_PLPAIN, // painSound
                StateNum.S_NULL, // Melee state S_NULL
                StateNum.S_SPOS_ATK1, StateNum.S_SPOS_DIE1, StateNum.S_SPOS_XDIE1,
                SoundKey.SFX_PODTH2, // deathSound
                8, 20.0, 56.0, 100, 0, // speed, radius, height, mass, damage
                SoundKey.SFX_NONE, // activeSound
                MobjFlags.MF_SOLID | MobjFlags.MF_SHOOTABLE | MobjFlags.MF_COUNTKILL,
                StateNum.S_SPOS_RAISE1
        );
        mobjInfos.put(MobjType.MT_SHOTGUY, shotguyInfo);
        doomedNumToMobjType.put(TYPE_SHOTGUY, MobjType.MT_SHOTGUY);
    }

    private void populateImp() { // Imp (MT_TROOP)
        // States: S_TROO_STND (214) to S_TROO_RAISE5 (252)
        // Sprite: SPR_TROO (index 31)
        addState(StateNum.S_TROO_STND, SpriteNames.TROO, 0, 10, Actions.A_LOOK, StateNum.S_TROO_STND2);
        addState(StateNum.S_TROO_STND2, SpriteNames.TROO, 1, 10, Actions.A_LOOK, StateNum.S_TROO_STND);

        // Run: 8 frames (0,0,1,1,2,2,3,3), 3 tics each
        addState(StateNum.S_TROO_RUN1, SpriteNames.TROO, 0, 3, Actions.A_CHASE, StateNum.S_TROO_RUN2);
        addState(StateNum.S_TROO_RUN2, SpriteNames.TROO, 0, 3, Actions.A_CHASE, StateNum.S_TROO_RUN3);
        addState(StateNum.S_TROO_RUN3, SpriteNames.TROO, 1, 3, Actions.A_CHASE, StateNum.S_TROO_RUN4);
        addState(StateNum.S_TROO_RUN4, SpriteNames.TROO, 1, 3, Actions.A_CHASE, StateNum.S_TROO_RUN5);
        addState(StateNum.S_TROO_RUN5, SpriteNames.TROO, 2, 3, Actions.A_CHASE, StateNum.S_TROO_RUN6);
        addState(StateNum.S_TROO_RUN6, SpriteNames.TROO, 2, 3, Actions.A_CHASE, StateNum.S_TROO_RUN7);
        addState(StateNum.S_TROO_RUN7, SpriteNames.TROO, 3, 3, Actions.A_CHASE, StateNum.S_TROO_RUN8);
        addState(StateNum.S_TROO_RUN8, SpriteNames.TROO, 3, 3, Actions.A_CHASE, StateNum.S_TROO_RUN1);

        // Melee Attack: 3 frames (6,7,6), tics (8,8,6) - TROOG, TROOH, TROOG
        addState(StateNum.S_TROO_ATK1, SpriteNames.TROO, 6, 8, Actions.A_FACE_TARGET, StateNum.S_TROO_ATK2);
        addState(StateNum.S_TROO_ATK2, SpriteNames.TROO, 7, 8, Actions.A_FACE_TARGET, StateNum.S_TROO_ATK3);
        addState(StateNum.S_TROO_ATK3, SpriteNames.TROO, 6, 6, Actions.A_TROOP_ATTACK, StateNum.S_TROO_RUN1);

        // Missile Attack: 3 frames (6,7,6), tics (8,8,8) - TROOG, TROOH, TROOG (same as melee for compatibility)
        addState(StateNum.S_TROO_MISS1, SpriteNames.TROO, 6, 8, Actions.A_FACE_TARGET, StateNum.S_TROO_MISS2);
        addState(StateNum.S_TROO_MISS2, SpriteNames.TROO, 7, 8, Actions.A_TROOP_MISSILE, StateNum.S_TROO_MISS3);
        addState(StateNum.S_TROO_MISS3, SpriteNames.TROO, 6, 8, Actions.NULL, StateNum.S_TROO_RUN1);

        // Pain: 2 frames (6,6), tics (3,3) - TROOG (use running frame instead of potentially problematic K frame)
        addState(StateNum.S_TROO_PAIN, SpriteNames.TROO, 6, 3, Actions.A_PAIN, StateNum.S_TROO_PAIN2);
        addState(StateNum.S_TROO_PAIN2, SpriteNames.TROO, 6, 3, Actions.NULL, StateNum.S_TROO_RUN1);

        // Die: 6 frames (11,12,13,14,15,16), tics (8,8,6,6,6,-1) - using more conservative frame indices
        addState(StateNum.S_TROO_DIE1, SpriteNames.TROO, 7, 8, Actions.NULL, StateNum.S_TROO_DIE2);
        addState(StateNum.S_TROO_DIE2, SpriteNames.TROO, 8, 8, Actions.A_SCREAM, StateNum.S_TROO_DIE3);
        addState(StateNum.S_TROO_DIE3, SpriteNames.TROO, 9, 6, Actions.NULL, StateNum.S_TROO_DIE4);
        addState(StateNum.S_TROO_DIE4, SpriteNames.TROO, 10, 6, Actions.A_FALL, StateNum.S_TROO_DIE5);
        addState(StateNum.S_TROO_DIE5, SpriteNames.TROO, 11, 6, Actions.NULL, StateNum.S_TROO_DIE6);
        addState(StateNum.S_TROO_DIE6, SpriteNames.TROO, 12, -1, Actions.NULL, StateNum.S_TROO_DIE6); // Final dead body frame M

        // XDeath: 8 frames (17,18,19,20,21,22,23,24), tics (5,5,5,5,5,5,5,-1) - TROOR to TROOY
        addState(StateNum.S_TROO_XDIE1, SpriteNames.TROO, 17, 5, Actions.NULL, StateNum.S_TROO_XDIE2);
        addState(StateNum.S_TROO_XDIE2, SpriteNames.TROO, 18, 5, Actions.A_XSCREAM, StateNum.S_TROO_XDIE3);
        addState(StateNum.S_TROO_XDIE3, SpriteNames.TROO, 19, 5, Actions.NULL, StateNum.S_TROO_XDIE4);
        addState(StateNum.S_TROO_XDIE4, SpriteNames.TROO, 20, 5, Actions.A_FALL, StateNum.S_TROO_XDIE5);
        addState(StateNum.S_TROO_XDIE5, SpriteNames.TROO, 21, 5, Actions.NULL, StateNum.S_TROO_XDIE6);
        addState(StateNum.S_TROO_XDIE6, SpriteNames.TROO, 22, 5, Actions.NULL, StateNum.S_TROO_XDIE7);
        addState(StateNum.S_TROO_XDIE7, SpriteNames.TROO, 23, 5, Actions.NULL, StateNum.S_TROO_XDIE8);
        addState(StateNum.S_TROO_XDIE8, SpriteNames.TROO, 24, -1, Actions.NULL, StateNum.S_TROO_XDIE8);

        // Raise: 5 frames (16,15,14,13,12), tics (8,8,6,6,8) - Reverse of death sequence
        addState(StateNum.S_TROO_RAISE1, SpriteNames.TROO, 16, 8, Actions.NULL, StateNum.S_TROO_RAISE2);
        addState(StateNum.S_TROO_RAISE2, SpriteNames.TROO, 15, 8, Actions.NULL, StateNum.S_TROO_RAISE3);
        addState(StateNum.S_TROO_RAISE3, SpriteNames.TROO, 14, 6, Actions.NULL, StateNum.S_TROO_RAISE4);
        addState(StateNum.S_TROO_RAISE4, SpriteNames.TROO, 13, 6, Actions.NULL, StateNum.S_TROO_RAISE5);
        addState(StateNum.S_TROO_RAISE5, SpriteNames.TROO, 12, 8, Actions.NULL, StateNum.S_TROO_RUN1);

        // MobjInfo for MT_TROOP (Imp)
        MobjInfoDef troopInfo = new MobjInfoDef(
                "MT_TROOP", TYPE_TROOP, StateNum.S_TROO_STND, 60, StateNum.S_TROO_RUN1,
                SoundKey.SFX_BGSIT1, // seeSound
                8, // reactiontime
                SoundKey.SFX_CLAW, // attackSound
                StateNum.S_TROO_PAIN, 200, // painstate, painchance
                SoundKey.SFX_BGPAIN, // painSound
                StateNum.S_TROO_ATK1, // melee attack
                StateNum.S_TROO_MISS1, StateNum.S_TROO_DIE1, StateNum.S_TROO_XDIE1,
                SoundKey.SFX_BGDTH1, // deathSound
                8, 20.0, 56.0, 100, 3, // speed, radius, height, mass, damage (melee)
                SoundKey.SFX_BGACT, // activeSound
                MobjFlags.MF_SOLID | MobjFlags.MF_SHOOTABLE | MobjFlags.MF_COUNTKILL,
                StateNum.S_TROO_RAISE1
        );
        mobjInfos.put(MobjType.MT_TROOP, troopInfo);
        doomedNumToMobjType.put(TYPE_TROOP, MobjType.MT_TROOP);
    }

    private void populateDemon() { // Demon/Pinky (MT_SERGEANT)
        // States: S_SARG_STND (253) to S_SARG_RAISE6 (290)
        // Sprite: SPR_SARG (index 32)
        addState(StateNum.S_SARG_STND, SpriteNames.SARG, 0, 10, Actions.A_LOOK, StateNum.S_SARG_STND2);
        addState(StateNum.S_SARG_STND2, SpriteNames.SARG, 1, 10, Actions.A_LOOK, StateNum.S_SARG_STND);

        // Run: 8 frames (0,0,1,1,2,2,3,3), 2 tics each (fast)
        addState(StateNum.S_SARG_RUN1, SpriteNames.SARG, 0, 2, Actions.A_CHASE, StateNum.S_SARG_RUN2);
        addState(StateNum.S_SARG_RUN2, SpriteNames.SARG, 0, 2, Actions.A_CHASE, StateNum.S_SARG_RUN3);
        addState(StateNum.S_SARG_RUN3, SpriteNames.SARG, 1, 2, Actions.A_CHASE, StateNum.S_SARG_RUN4);
        addState(StateNum.S_SARG_RUN4, SpriteNames.SARG, 1, 2, Actions.A_CHASE, StateNum.S_SARG_RUN5);
        addState(StateNum.S_SARG_RUN5, SpriteNames.SARG, 2, 2, Actions.A_CHASE, StateNum.S_SARG_RUN6);
        addState(StateNum.S_SARG_RUN6, SpriteNames.SARG, 2, 2, Actions.A_CHASE, StateNum.S_SARG_RUN7);
        addState(StateNum.S_SARG_RUN7, SpriteNames.SARG, 3, 2, Actions.A_CHASE, StateNum.S_SARG_RUN8);
        addState(StateNum.S_SARG_RUN8, SpriteNames.SARG, 3, 2, Actions.A_CHASE, StateNum.S_SARG_RUN1);

        // Melee Attack: 4 frames (4,5,6,7), tics (8,8,8,8)
        addState(StateNum.S_SARG_ATK1, SpriteNames.SARG, 4, 8, Actions.A_FACE_TARGET, StateNum.S_SARG_ATK2);
        addState(StateNum.S_SARG_ATK2, SpriteNames.SARG, 5, 8, Actions.A_FACE_TARGET, StateNum.S_SARG_ATK3);
        addState(StateNum.S_SARG_ATK3, SpriteNames.SARG, 6, 8, Actions.A_SARG_ATTACK, StateNum.S_SARG_ATK4);
        addState(StateNum.S_SARG_ATK4, SpriteNames.SARG, 7, 8, Actions.NULL, StateNum.S_SARG_RUN1);

        // Pain: 2 frames (8,8), tics (2,2)
        addState(StateNum.S_SARG_PAIN, SpriteNames.SARG, 8, 2, Actions.NULL, StateNum.S_SARG_PAIN2);
        addState(StateNum.S_SARG_PAIN2, SpriteNames.SARG, 8, 2, Actions.A_PAIN, StateNum.S_SARG_RUN1);

        // Die: 6 frames (9,10,11,12,13,14), tics (8,8,4,4,4,-1)
        addState(StateNum.S_SARG_DIE1, SpriteNames.SARG, 9, 8, Actions.NULL, StateNum.S_SARG_DIE2);
        addState(StateNum.S_SARG_DIE2, SpriteNames.SARG, 10, 8, Actions.A_SCREAM, StateNum.S_SARG_DIE3);
        addState(StateNum.S_SARG_DIE3, SpriteNames.SARG, 11, 4, Actions.NULL, StateNum.S_SARG_DIE4);
        addState(StateNum.S_SARG_DIE4, SpriteNames.SARG, 12, 4, Actions.A_FALL, StateNum.S_SARG_DIE5);
        addState(StateNum.S_SARG_DIE5, SpriteNames.SARG, 13, 4, Actions.NULL, StateNum.S_SARG_DIE6);
        addState(StateNum.S_SARG_DIE6, SpriteNames.SARG, 14, -1, Actions.NULL, StateNum.S_SARG_DIE6);

        // Raise: 6 frames (13,12,11,10,9), tics (5,5,6,6,8)
        addState(StateNum.S_SARG_RAISE1, SpriteNames.SARG, 13, 5, Actions.NULL, StateNum.S_SARG_RAISE2);
        addState(StateNum.S_SARG_RAISE2, SpriteNames.SARG, 12, 5, Actions.NULL, StateNum.S_SARG_RAISE3);
        addState(StateNum.S_SARG_RAISE3, SpriteNames.SARG, 11, 6, Actions.NULL, StateNum.S_SARG_RAISE4);
        addState(StateNum.S_SARG_RAISE4, SpriteNames.SARG, 10, 6, Actions.NULL, StateNum.S_SARG_RAISE5);
        addState(StateNum.S_SARG_RAISE5, SpriteNames.SARG, 9, 8, Actions.NULL, StateNum.S_SARG_RUN1);

        // MobjInfo for MT_SERGEANT (Demon/Pinky)
        MobjInfoDef demonInfo = new MobjInfoDef(
                "MT_SERGEANT", TYPE_SERGEANT, StateNum.S_SARG_STND, 150, StateNum.S_SARG_RUN1,
                SoundKey.SFX_SGTSIT, // seeSound
                8, // reactiontime
                SoundKey.SFX_SGTATK, // attackSound
                StateNum.S_SARG_PAIN, 180, // painstate, painchance
                SoundKey.SFX_PLPAIN, // painSound
                StateNum.S_SARG_ATK1, // melee only
                StateNum.S_NULL, StateNum.S_SARG_DIE1, StateNum.S_NULL, // no missile, death, xdeath
                SoundKey.SFX_PODTH3, // deathSound
                10, 30.0, 56.0, 400, 4, // speed, radius, height, mass, damage (strong bite)
                SoundKey.SFX_NONE, // activeSound
                MobjFlags.MF_SOLID | MobjFlags.MF_SHOOTABLE | MobjFlags.MF_COUNTKILL,
                StateNum.S_SARG_RAISE1
        );
        mobjInfos.put(MobjType.MT_SERGEANT, demonInfo);
        doomedNumToMobjType.put(TYPE_SERGEANT, MobjType.MT_SERGEANT);
    }

    private void populateProjectiles() {
        // MT_TROOPSHOT (Imp Fireball) states
        addState(StateNum.S_TBALL1, SpriteNames.BAL1, 0, 4, Actions.NULL, StateNum.S_TBALL2);
        addState(StateNum.S_TBALL2, SpriteNames.BAL1, 1, 4, Actions.NULL, StateNum.S_TBALL1);

        // Fireball explosion
        addState(StateNum.S_TBALLEX1, SpriteNames.BAL1, 2, 6, Actions.NULL, StateNum.S_TBALLEX2);
        addState(StateNum.S_TBALLEX2, SpriteNames.BAL1, 3, 6, Actions.NULL, StateNum.S_TBALLEX3);
        addState(StateNum.S_TBALLEX3, SpriteNames.BAL1, 4, 6, Actions.NULL, StateNum.S_NULL);

        // MobjInfo for MT_TROOPSHOT (Imp Fireball)
        MobjInfoDef fireballInfo = new MobjInfoDef(
                "MT_TROOPSHOT", -1, StateNum.S_TBALL1, 1000, StateNum.S_NULL,
                SoundKey.SFX_NONE, // seeSound
                8, // reactiontime
                SoundKey.SFX_NONE, // attackSound
                StateNum.S_NULL, 0, // painstate, painchance
                SoundKey.SFX_NONE, // painSound
                StateNum.S_NULL, // meleestate
                StateNum.S_NULL, StateNum.S_TBALLEX1, StateNum.S_NULL, // missile, death, xdeath
                SoundKey.SFX_FIRXPL, // deathSound (fireball explosion)
                10, 6.0, 8.0, 100, 3, // speed, radius, height, mass, damage
                SoundKey.SFX_NONE, // activeSound
                MobjFlags.MF_NOBLOCKMAP | MobjFlags.MF_MISSILE | MobjFlags.MF_DROPOFF | MobjFlags.MF_NOGRAVITY,
                StateNum.S_NULL
        );
        mobjInfos.put(MobjType.MT_TROOPSHOT, fireballInfo);

        // MT_PUFF (Bullet Impact) states
        addState(StateNum.S_PUFF1, SpriteNames.PUFF, 0, 4, Actions.NULL, StateNum.S_PUFF2);
        addState(StateNum.S_PUFF2, SpriteNames.PUFF, 1, 4, Actions.NULL, StateNum.S_PUFF3);
        addState(StateNum.S_PUFF3, SpriteNames.PUFF, 2, 4, Actions.NULL, StateNum.S_PUFF4);
        addState(StateNum.S_PUFF4, SpriteNames.PUFF, 3, 4, Actions.NULL, StateNum.S_NULL);

        // MobjInfo for MT_PUFF (Bullet Impact)
        MobjInfoDef puffInfo = new MobjInfoDef(
                "MT_PUFF", -1, StateNum.S_PUFF1, 1000, StateNum.S_NULL,
                SoundKey.SFX_NONE, // seeSound
                0, // reactiontime
                SoundKey.SFX_NONE, // attackSound
                StateNum.S_NULL, 0, // painstate, painchance
                SoundKey.SFX_NONE, // painSound
                StateNum.S_NULL, // meleestate
                StateNum.S_NULL, StateNum.S_NULL, StateNum.S_NULL, // missile, death, xdeath
                SoundKey.SFX_NONE, // deathSound
                0, 20.0, 16.0, 100, 0, // no speed, radius, height, mass, no damage
                SoundKey.SFX_NONE, // activeSound
                MobjFlags.MF_NOBLOCKMAP | MobjFlags.MF_NOGRAVITY,
                StateNum.S_NULL
        );
        mobjInfos.put(MobjType.MT_PUFF, puffInfo);

        // MT_BLOOD (Blood Splat) states
        addState(StateNum.S_BLOOD1, SpriteNames.BLUD, 2, 8, Actions.NULL, StateNum.S_BLOOD2);
        addState(StateNum.S_BLOOD2, SpriteNames.BLUD, 1, 8, Actions.NULL, StateNum.S_BLOOD3);
        addState(StateNum.S_BLOOD3, SpriteNames.BLUD, 0, 8, Actions.NULL, StateNum.S_NULL);

        // MobjInfo for MT_BLOOD (Blood Splat)
        MobjInfoDef bloodInfo = new MobjInfoDef(
                "MT_BLOOD", -1, StateNum.S_BLOOD1, 1000, StateNum.S_NULL,
                SoundKey.SFX_NONE, // seeSound
                0, // reactiontime
                SoundKey.SFX_NONE, // attackSound
                StateNum.S_NULL, 0, // painstate, painchance
                SoundKey.SFX_NONE, // painSound
                StateNum.S_NULL, // meleestate
                StateNum.S_NULL, StateNum.S_NULL, StateNum.S_NULL, // missile, death, xdeath
                SoundKey.SFX_NONE, // deathSound
                0, 20.0, 16.0, 100, 0, // no speed, radius, height, mass, no damage
                SoundKey.SFX_NONE, // activeSound
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

    private void populateWeapons() {
        // Pistol weapon states (following original Doom info.c)
        addState(StateNum.S_PISTOL, SpriteNames.PISG, 0, 1, Actions.A_WEAPON_READY, StateNum.S_PISTOL);
        addState(StateNum.S_PISTOL1, SpriteNames.PISG, 0, 4, Actions.NULL, StateNum.S_PISTOL2);
        addState(StateNum.S_PISTOL2, SpriteNames.PISG, 1, 6, Actions.A_FIRE_PISTOL, StateNum.S_PISTOL3);
        addState(StateNum.S_PISTOL3, SpriteNames.PISG, 2, 4, Actions.NULL, StateNum.S_PISTOL4);
        addState(StateNum.S_PISTOL4, SpriteNames.PISG, 1, 5, Actions.A_RE_FIRE, StateNum.S_PISTOL);
        addState(StateNum.S_PISTOLFLASH, SpriteNames.PISF, 0 | 0x8000, 7, Actions.A_LIGHT_1, StateNum.S_NULL);

        // Shotgun weapon states
        addState(StateNum.S_SGUN, SpriteNames.SHTG, 0, 1, Actions.A_WEAPON_READY, StateNum.S_SGUN);
        addState(StateNum.S_SGUN1, SpriteNames.SHTG, 0, 3, Actions.NULL, StateNum.S_SGUN2);
        addState(StateNum.S_SGUN2, SpriteNames.SHTG, 0, 7, Actions.A_FIRE_SHOTGUN, StateNum.S_SGUN3);
        addState(StateNum.S_SGUN3, SpriteNames.SHTG, 1, 5, Actions.NULL, StateNum.S_SGUN4);
        addState(StateNum.S_SGUN4, SpriteNames.SHTG, 2, 5, Actions.NULL, StateNum.S_SGUN5);
        addState(StateNum.S_SGUN5, SpriteNames.SHTG, 3, 4, Actions.NULL, StateNum.S_SGUN6);
        addState(StateNum.S_SGUN6, SpriteNames.SHTG, 2, 5, Actions.NULL, StateNum.S_SGUN7);
        addState(StateNum.S_SGUN7, SpriteNames.SHTG, 1, 5, Actions.NULL, StateNum.S_SGUN8);
        addState(StateNum.S_SGUN8, SpriteNames.SHTG, 0, 3, Actions.NULL, StateNum.S_SGUN9);
        addState(StateNum.S_SGUN9, SpriteNames.SHTG, 0, 7, Actions.A_RE_FIRE, StateNum.S_SGUN);
        addState(StateNum.S_SGUNFLASH1, SpriteNames.SHTF, 0 | 0x8000, 4, Actions.A_LIGHT_1, StateNum.S_SGUNFLASH2);
        addState(StateNum.S_SGUNFLASH2, SpriteNames.SHTF, 1 | 0x8000, 3, Actions.A_LIGHT_2, StateNum.S_NULL);

        // Chaingun weapon states  
        addState(StateNum.S_CHAIN, SpriteNames.MGUN, 0, 1, Actions.A_WEAPON_READY, StateNum.S_CHAIN);
        addState(StateNum.S_CHAIN1, SpriteNames.MGUN, 0, 4, Actions.A_FIRE_CGUN, StateNum.S_CHAIN2);
        addState(StateNum.S_CHAIN2, SpriteNames.MGUN, 1, 4, Actions.A_FIRE_CGUN, StateNum.S_CHAIN3);
        addState(StateNum.S_CHAIN3, SpriteNames.MGUN, 0, 0, Actions.A_RE_FIRE, StateNum.S_CHAIN);
        addState(StateNum.S_CHAINFLASH1, SpriteNames.CHGF, 0 | 0x8000, 5, Actions.A_LIGHT_1, StateNum.S_CHAINFLASH2);
        addState(StateNum.S_CHAINFLASH2, SpriteNames.CHGF, 1 | 0x8000, 5, Actions.A_LIGHT_2, StateNum.S_NULL);

        // Rocket launcher weapon states
        addState(StateNum.S_MISSILE, SpriteNames.LAUN, 0, 1, Actions.A_WEAPON_READY, StateNum.S_MISSILE);
        addState(StateNum.S_MISSILE1, SpriteNames.LAUN, 1, 8, Actions.A_FIRE_MISSILE, StateNum.S_MISSILE2);
        addState(StateNum.S_MISSILE2, SpriteNames.LAUN, 1, 12, Actions.NULL, StateNum.S_MISSILE3);
        addState(StateNum.S_MISSILE3, SpriteNames.LAUN, 0, 0, Actions.A_RE_FIRE, StateNum.S_MISSILE);
        addState(StateNum.S_MISSILEFLASH1, SpriteNames.MISL, 0 | 0x8000, 3, Actions.A_LIGHT_1, StateNum.S_MISSILEFLASH2);
        addState(StateNum.S_MISSILEFLASH2, SpriteNames.MISL, 1 | 0x8000, 4, Actions.NULL, StateNum.S_MISSILEFLASH3);
        addState(StateNum.S_MISSILEFLASH3, SpriteNames.MISL, 2 | 0x8000, 4, Actions.A_LIGHT_2, StateNum.S_MISSILEFLASH4);
        addState(StateNum.S_MISSILEFLASH4, SpriteNames.MISL, 3 | 0x8000, 4, Actions.A_LIGHT_2, StateNum.S_NULL);

        // Plasma rifle weapon states
        addState(StateNum.S_PLASMA, SpriteNames.PLAS, 0, 1, Actions.A_WEAPON_READY, StateNum.S_PLASMA);
        addState(StateNum.S_PLASMA1, SpriteNames.PLAS, 0, 3, Actions.A_FIRE_PLASMA, StateNum.S_PLASMA2);
        addState(StateNum.S_PLASMA2, SpriteNames.PLAS, 1, 20, Actions.A_RE_FIRE, StateNum.S_PLASMA);
        addState(StateNum.S_PLASMAFLASH1, SpriteNames.PLSF, 0 | 0x8000, 4, Actions.A_LIGHT_1, StateNum.S_PLASMAFLASH2);
        addState(StateNum.S_PLASMAFLASH2, SpriteNames.PLSF, 1 | 0x8000, 4, Actions.A_LIGHT_1, StateNum.S_NULL);

        // BFG weapon states
        addState(StateNum.S_BFG, SpriteNames.BFUG, 0, 1, Actions.A_WEAPON_READY, StateNum.S_BFG);
        addState(StateNum.S_BFG1, SpriteNames.BFUG, 0, 20, Actions.NULL, StateNum.S_BFG2);
        addState(StateNum.S_BFG2, SpriteNames.BFUG, 0, 10, Actions.A_FIRE_BFG, StateNum.S_BFG3);
        addState(StateNum.S_BFG3, SpriteNames.BFUG, 1, 10, Actions.NULL, StateNum.S_BFG4);
        addState(StateNum.S_BFG4, SpriteNames.BFUG, 1, 20, Actions.A_RE_FIRE, StateNum.S_BFG);
        addState(StateNum.S_BFGFLASH1, SpriteNames.BFGF, 0 | 0x8000, 11, Actions.A_LIGHT_1, StateNum.S_BFGFLASH2);
        addState(StateNum.S_BFGFLASH2, SpriteNames.BFGF, 1 | 0x8000, 6, Actions.A_LIGHT_2, StateNum.S_NULL);
    }

    private void populateItems() {
        // Add state definitions for items
        addState(StateNum.S_STIM, SpriteNames.STIM, 0, -1, Actions.NULL, StateNum.S_STIM);
        addState(StateNum.S_MEDI, SpriteNames.MEDI, 0, -1, Actions.NULL, StateNum.S_MEDI);
        addState(StateNum.S_ARM1, SpriteNames.ARM1, 0, -1, Actions.NULL, StateNum.S_ARM1);
        addState(StateNum.S_ARM2, SpriteNames.ARM2, 0, -1, Actions.NULL, StateNum.S_ARM2);
        addState(StateNum.S_BON1, SpriteNames.BON1, 0, -1, Actions.NULL, StateNum.S_BON1);
        addState(StateNum.S_BON2, SpriteNames.BON2, 0, -1, Actions.NULL, StateNum.S_BON2);
        addState(StateNum.S_CLIP, SpriteNames.CLIP, 0, -1, Actions.NULL, StateNum.S_CLIP);
        addState(StateNum.S_SHEL, SpriteNames.SHEL, 0, -1, Actions.NULL, StateNum.S_SHEL);

        // Health items
        MobjInfoDef stimpakInfo = new MobjInfoDef(
                "MT_STIMPACK", 2011, StateNum.S_STIM, -1, StateNum.S_NULL,
                SoundKey.SFX_NONE, 0, SoundKey.SFX_NONE, StateNum.S_NULL, 0,
                SoundKey.SFX_NONE, StateNum.S_NULL, StateNum.S_NULL, StateNum.S_NULL, StateNum.S_NULL,
                SoundKey.SFX_NONE, 0, 20.0, 16.0, 100, 0,
                SoundKey.SFX_NONE, MobjFlags.MF_SPECIAL, StateNum.S_NULL
        );
        mobjInfos.put(MobjType.MT_STIMPACK, stimpakInfo);
        doomedNumToMobjType.put(2011, MobjType.MT_STIMPACK);

        MobjInfoDef medikitInfo = new MobjInfoDef(
                "MT_MEDIKIT", 2012, StateNum.S_MEDI, -1, StateNum.S_NULL,
                SoundKey.SFX_NONE, 0, SoundKey.SFX_NONE, StateNum.S_NULL, 0,
                SoundKey.SFX_NONE, StateNum.S_NULL, StateNum.S_NULL, StateNum.S_NULL, StateNum.S_NULL,
                SoundKey.SFX_NONE, 0, 20.0, 16.0, 100, 0,
                SoundKey.SFX_NONE, MobjFlags.MF_SPECIAL, StateNum.S_NULL
        );
        mobjInfos.put(MobjType.MT_MEDIKIT, medikitInfo);
        doomedNumToMobjType.put(2012, MobjType.MT_MEDIKIT);

        // Armor
        MobjInfoDef greenArmorInfo = new MobjInfoDef(
                "MT_GREENARMOR", 2018, StateNum.S_ARM1, -1, StateNum.S_NULL,
                SoundKey.SFX_NONE, 0, SoundKey.SFX_NONE, StateNum.S_NULL, 0,
                SoundKey.SFX_NONE, StateNum.S_NULL, StateNum.S_NULL, StateNum.S_NULL, StateNum.S_NULL,
                SoundKey.SFX_NONE, 0, 20.0, 16.0, 100, 0,
                SoundKey.SFX_NONE, MobjFlags.MF_SPECIAL, StateNum.S_NULL
        );
        mobjInfos.put(MobjType.MT_GREENARMOR, greenArmorInfo);
        doomedNumToMobjType.put(2018, MobjType.MT_GREENARMOR);

        // Ammo
        MobjInfoDef clipInfo = new MobjInfoDef(
                "MT_CLIP", 2007, StateNum.S_CLIP, -1, StateNum.S_NULL,
                SoundKey.SFX_NONE, 0, SoundKey.SFX_NONE, StateNum.S_NULL, 0,
                SoundKey.SFX_NONE, StateNum.S_NULL, StateNum.S_NULL, StateNum.S_NULL, StateNum.S_NULL,
                SoundKey.SFX_NONE, 0, 20.0, 16.0, 100, 0,
                SoundKey.SFX_NONE, MobjFlags.MF_SPECIAL, StateNum.S_NULL
        );
        mobjInfos.put(MobjType.MT_CLIP, clipInfo);
        doomedNumToMobjType.put(2007, MobjType.MT_CLIP);

        MobjInfoDef shellsInfo = new MobjInfoDef(
                "MT_SHELLS", 2008, StateNum.S_SHEL, -1, StateNum.S_NULL,
                SoundKey.SFX_NONE, 0, SoundKey.SFX_NONE, StateNum.S_NULL, 0,
                SoundKey.SFX_NONE, StateNum.S_NULL, StateNum.S_NULL, StateNum.S_NULL, StateNum.S_NULL,
                SoundKey.SFX_NONE, 0, 20.0, 16.0, 100, 0,
                SoundKey.SFX_NONE, MobjFlags.MF_SPECIAL, StateNum.S_NULL
        );
        mobjInfos.put(MobjType.MT_SHELLS, shellsInfo);
        doomedNumToMobjType.put(2008, MobjType.MT_SHELLS);

        // Health bonus
        MobjInfoDef healthBonusInfo = new MobjInfoDef(
                "MT_HEALTH_BONUS", 2014, StateNum.S_BON1, -1, StateNum.S_NULL,
                SoundKey.SFX_NONE, 0, SoundKey.SFX_NONE, StateNum.S_NULL, 0,
                SoundKey.SFX_NONE, StateNum.S_NULL, StateNum.S_NULL, StateNum.S_NULL, StateNum.S_NULL,
                SoundKey.SFX_NONE, 0, 20.0, 16.0, 100, 0,
                SoundKey.SFX_NONE, MobjFlags.MF_SPECIAL | MobjFlags.MF_COUNTITEM, StateNum.S_NULL
        );
        mobjInfos.put(MobjType.MT_HEALTH_BONUS, healthBonusInfo);
        doomedNumToMobjType.put(2014, MobjType.MT_HEALTH_BONUS);

        // Armor bonus  
        MobjInfoDef armorBonusInfo = new MobjInfoDef(
                "MT_ARMOR_BONUS", 2015, StateNum.S_BON2, -1, StateNum.S_NULL,
                SoundKey.SFX_NONE, 0, SoundKey.SFX_NONE, StateNum.S_NULL, 0,
                SoundKey.SFX_NONE, StateNum.S_NULL, StateNum.S_NULL, StateNum.S_NULL, StateNum.S_NULL,
                SoundKey.SFX_NONE, 0, 20.0, 16.0, 100, 0,
                SoundKey.SFX_NONE, MobjFlags.MF_SPECIAL | MobjFlags.MF_COUNTITEM, StateNum.S_NULL
        );
        mobjInfos.put(MobjType.MT_ARMOR_BONUS, armorBonusInfo);
        doomedNumToMobjType.put(2015, MobjType.MT_ARMOR_BONUS);
    }

    private void populateDecorations() {
        // Add state definitions for decorations
        addState(StateNum.S_BAR1, SpriteNames.BAR1, 0, -1, Actions.NULL, StateNum.S_BAR1);
        addState(StateNum.S_ELEC, SpriteNames.ELEC, 0, -1, Actions.NULL, StateNum.S_ELEC);
        addState(StateNum.S_BEXP, SpriteNames.BEXP, 0, 5, Actions.A_SCREAM, StateNum.S_BEXP2);
        addState(StateNum.S_BEXP2, SpriteNames.BEXP, 1, 5, Actions.NULL, StateNum.S_BEXP3);
        addState(StateNum.S_BEXP3, SpriteNames.BEXP, 2, 5, Actions.A_EXPLODE, StateNum.S_BEXP4);
        addState(StateNum.S_BEXP4, SpriteNames.BEXP, 3, 10, Actions.NULL, StateNum.S_BEXP5);
        addState(StateNum.S_BEXP5, SpriteNames.BEXP, 4, 10, Actions.A_FALL, StateNum.S_NULL);

        // Exploding barrel
        MobjInfoDef barrelInfo = new MobjInfoDef(
                "MT_BARREL", 2035, StateNum.S_BAR1, 20, StateNum.S_NULL,
                SoundKey.SFX_NONE, 8, SoundKey.SFX_NONE, StateNum.S_NULL, 0,
                SoundKey.SFX_NONE, StateNum.S_NULL, StateNum.S_NULL, StateNum.S_BEXP, StateNum.S_NULL,
                SoundKey.SFX_BAREXP, 0, 10.0, 42.0, 100, 0,
                SoundKey.SFX_NONE, MobjFlags.MF_SOLID | MobjFlags.MF_SHOOTABLE | MobjFlags.MF_NOBLOOD, StateNum.S_NULL
        );
        mobjInfos.put(MobjType.MT_BARREL, barrelInfo);
        doomedNumToMobjType.put(2035, MobjType.MT_BARREL);


        // Dead marine (decoration)
        MobjInfoDef deadMarineInfo = new MobjInfoDef(
                "MT_DEAD_MARINE", 15, StateNum.S_PLAY_DIE7, -1, StateNum.S_NULL,
                SoundKey.SFX_NONE, 0, SoundKey.SFX_NONE, StateNum.S_NULL, 0,
                SoundKey.SFX_NONE, StateNum.S_NULL, StateNum.S_NULL, StateNum.S_NULL, StateNum.S_NULL,
                SoundKey.SFX_NONE, 0, 20.0, 16.0, 100, 0,
                SoundKey.SFX_NONE, 0, StateNum.S_NULL
        );
        mobjInfos.put(MobjType.MT_DEAD_MARINE, deadMarineInfo);
        doomedNumToMobjType.put(15, MobjType.MT_DEAD_MARINE);

        // Tall techno pillar
        MobjInfoDef techPillarInfo = new MobjInfoDef(
                "MT_TECHPILLAR", 48, StateNum.S_ELEC, -1, StateNum.S_NULL,
                SoundKey.SFX_NONE, 0, SoundKey.SFX_NONE, StateNum.S_NULL, 0,
                SoundKey.SFX_NONE, StateNum.S_NULL, StateNum.S_NULL, StateNum.S_NULL, StateNum.S_NULL,
                SoundKey.SFX_NONE, 0, 16.0, 16.0, 100, 0,
                SoundKey.SFX_NONE, MobjFlags.MF_SOLID, StateNum.S_NULL
        );
        mobjInfos.put(MobjType.MT_TECHPILLAR, techPillarInfo);
        doomedNumToMobjType.put(48, MobjType.MT_TECHPILLAR);
    }

    private void populateSpecialObjects() {
        // Add invisible state for special objects
        addState(StateNum.S_INVISIBLE, SpriteNames.TROO, -1, -1, Actions.NULL, StateNum.S_INVISIBLE);

        // Deathmatch start
        MobjInfoDef deathmatchStartInfo = new MobjInfoDef(
                "MT_DEATHMATCH_START", 11, StateNum.S_INVISIBLE, -1, StateNum.S_NULL,
                SoundKey.SFX_NONE, 0, SoundKey.SFX_NONE, StateNum.S_NULL, 0,
                SoundKey.SFX_NONE, StateNum.S_NULL, StateNum.S_NULL, StateNum.S_NULL, StateNum.S_NULL,
                SoundKey.SFX_NONE, 0, 20.0, 16.0, 100, 0,
                SoundKey.SFX_NONE, MobjFlags.MF_NOSECTOR | MobjFlags.MF_NOBLOCKMAP, StateNum.S_NULL
        );
        mobjInfos.put(MobjType.MT_DEATHMATCH_START, deathmatchStartInfo);
        doomedNumToMobjType.put(11, MobjType.MT_DEATHMATCH_START);

        // Teleporter destination
        MobjInfoDef teleporterDestInfo = new MobjInfoDef(
                "MT_TELEPORTER_DEST", 14, StateNum.S_INVISIBLE, -1, StateNum.S_NULL,
                SoundKey.SFX_NONE, 0, SoundKey.SFX_NONE, StateNum.S_NULL, 0,
                SoundKey.SFX_NONE, StateNum.S_NULL, StateNum.S_NULL, StateNum.S_NULL, StateNum.S_NULL,
                SoundKey.SFX_NONE, 0, 20.0, 16.0, 100, 0,
                SoundKey.SFX_NONE, MobjFlags.MF_NOSECTOR | MobjFlags.MF_NOBLOCKMAP, StateNum.S_NULL
        );
        mobjInfos.put(MobjType.MT_TELEPORTER_DEST, teleporterDestInfo);
        doomedNumToMobjType.put(14, MobjType.MT_TELEPORTER_DEST);
    }

    private void populateKeys() {
        // Add key states
        addState(StateNum.S_BKEY, SpriteNames.BKEY, 0, -1, Actions.NULL, StateNum.S_BKEY);
        addState(StateNum.S_YKEY, SpriteNames.YKEY, 0, -1, Actions.NULL, StateNum.S_YKEY);
        addState(StateNum.S_RKEY, SpriteNames.RKEY, 0, -1, Actions.NULL, StateNum.S_RKEY);
        addState(StateNum.S_BSKU, SpriteNames.BSKU, 0, -1, Actions.NULL, StateNum.S_BSKU);
        addState(StateNum.S_YSKU, SpriteNames.YSKU, 0, -1, Actions.NULL, StateNum.S_YSKU);
        addState(StateNum.S_RSKU, SpriteNames.RSKU, 0, -1, Actions.NULL, StateNum.S_RSKU);

        // Blue keycard
        MobjInfoDef blueKeyInfo = new MobjInfoDef(
                "MT_BLUEKEY", 5, StateNum.S_BKEY, -1, StateNum.S_NULL,
                SoundKey.SFX_NONE, 0, SoundKey.SFX_NONE, StateNum.S_NULL, 0,
                SoundKey.SFX_NONE, StateNum.S_NULL, StateNum.S_NULL, StateNum.S_NULL, StateNum.S_NULL,
                SoundKey.SFX_NONE, 0, 20.0, 16.0, 100, 0,
                SoundKey.SFX_NONE, MobjFlags.MF_SPECIAL | MobjFlags.MF_NOTDMATCH, StateNum.S_NULL
        );
        mobjInfos.put(MobjType.MT_BLUEKEY, blueKeyInfo);
        doomedNumToMobjType.put(5, MobjType.MT_BLUEKEY);

        // Yellow keycard
        MobjInfoDef yellowKeyInfo = new MobjInfoDef(
                "MT_YELLOWKEY", 6, StateNum.S_YKEY, -1, StateNum.S_NULL,
                SoundKey.SFX_NONE, 0, SoundKey.SFX_NONE, StateNum.S_NULL, 0,
                SoundKey.SFX_NONE, StateNum.S_NULL, StateNum.S_NULL, StateNum.S_NULL, StateNum.S_NULL,
                SoundKey.SFX_NONE, 0, 20.0, 16.0, 100, 0,
                SoundKey.SFX_NONE, MobjFlags.MF_SPECIAL | MobjFlags.MF_NOTDMATCH, StateNum.S_NULL
        );
        mobjInfos.put(MobjType.MT_YELLOWKEY, yellowKeyInfo);
        doomedNumToMobjType.put(6, MobjType.MT_YELLOWKEY);

        // Red keycard
        MobjInfoDef redKeyInfo = new MobjInfoDef(
                "MT_REDKEY", 13, StateNum.S_RKEY, -1, StateNum.S_NULL,
                SoundKey.SFX_NONE, 0, SoundKey.SFX_NONE, StateNum.S_NULL, 0,
                SoundKey.SFX_NONE, StateNum.S_NULL, StateNum.S_NULL, StateNum.S_NULL, StateNum.S_NULL,
                SoundKey.SFX_NONE, 0, 20.0, 16.0, 100, 0,
                SoundKey.SFX_NONE, MobjFlags.MF_SPECIAL | MobjFlags.MF_NOTDMATCH, StateNum.S_NULL
        );
        mobjInfos.put(MobjType.MT_REDKEY, redKeyInfo);
        doomedNumToMobjType.put(13, MobjType.MT_REDKEY);

        // Blue skull key
        MobjInfoDef blueSkullInfo = new MobjInfoDef(
                "MT_BLUESKULL", 39, StateNum.S_BSKU, -1, StateNum.S_NULL,
                SoundKey.SFX_NONE, 0, SoundKey.SFX_NONE, StateNum.S_NULL, 0,
                SoundKey.SFX_NONE, StateNum.S_NULL, StateNum.S_NULL, StateNum.S_NULL, StateNum.S_NULL,
                SoundKey.SFX_NONE, 0, 20.0, 16.0, 100, 0,
                SoundKey.SFX_NONE, MobjFlags.MF_SPECIAL | MobjFlags.MF_NOTDMATCH, StateNum.S_NULL
        );
        mobjInfos.put(MobjType.MT_BLUESKULL, blueSkullInfo);
        doomedNumToMobjType.put(39, MobjType.MT_BLUESKULL);

        // Yellow skull key
        MobjInfoDef yellowSkullInfo = new MobjInfoDef(
                "MT_YELLOWSKULL", 40, StateNum.S_YSKU, -1, StateNum.S_NULL,
                SoundKey.SFX_NONE, 0, SoundKey.SFX_NONE, StateNum.S_NULL, 0,
                SoundKey.SFX_NONE, StateNum.S_NULL, StateNum.S_NULL, StateNum.S_NULL, StateNum.S_NULL,
                SoundKey.SFX_NONE, 0, 20.0, 16.0, 100, 0,
                SoundKey.SFX_NONE, MobjFlags.MF_SPECIAL | MobjFlags.MF_NOTDMATCH, StateNum.S_NULL
        );
        mobjInfos.put(MobjType.MT_YELLOWSKULL, yellowSkullInfo);
        doomedNumToMobjType.put(40, MobjType.MT_YELLOWSKULL);

        // Red skull key
        MobjInfoDef redSkullInfo = new MobjInfoDef(
                "MT_REDSKULL", 38, StateNum.S_RSKU, -1, StateNum.S_NULL,
                SoundKey.SFX_NONE, 0, SoundKey.SFX_NONE, StateNum.S_NULL, 0,
                SoundKey.SFX_NONE, StateNum.S_NULL, StateNum.S_NULL, StateNum.S_NULL, StateNum.S_NULL,
                SoundKey.SFX_NONE, 0, 20.0, 16.0, 100, 0,
                SoundKey.SFX_NONE, MobjFlags.MF_SPECIAL | MobjFlags.MF_NOTDMATCH, StateNum.S_NULL
        );
        mobjInfos.put(MobjType.MT_REDSKULL, redSkullInfo);
        doomedNumToMobjType.put(38, MobjType.MT_REDSKULL);
    }
}

