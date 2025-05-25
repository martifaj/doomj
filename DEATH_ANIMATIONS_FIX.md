# Death Animations Fix

## Problem
- Exploding barrels disappeared after explosion (sound played but no visual)
- Enemies disappeared when killed (sound played but no visual)
- Zombieman death animation showed but final frame was incorrect

## Root Causes
1. **Sprite Rotation Issues**: Death frames and barrel explosions were using rotation 1 instead of rotation 0
2. **Missing Sprites**: Generated sprite names like `BEXPE1`, `POSSH1` don't exist in WAD files
3. **Wrong Final Frame**: Death sequences looped back to falling frame instead of staying on final dead body frame

## Solution

### 1. Fixed Barrel Explosion Rotation (`abb98b3`)
- Added "BEXP" to rotation 0 sprite list in `StateDef.java`
- Barrel explosions now use `BEXPE0` instead of `BEXPE1`
- Explosions are now visible

### 2. Fixed Enemy Death Sprite Rotation (`abaf613`)
- Enhanced `getRotationForSprite()` to accept frameIndex parameter
- Death frames (frameIndex >= 7) now use rotation 0 for all enemies
- Living frames (frameIndex < 7) still use rotation 1 for 8-directional sprites
- Death sprites: `POSSH0`, `POSSI0`, `POSSJ0`, `POSSK0`, `POSSL0` (instead of `POSSH1`, etc.)

### 3. Fixed Death Animation Final Frames (`430dc55`)
- **Zombieman (POSS)**: Final frame changed from H (index 7) to L (index 11) → `POSSL0`
- **Shotgun Guy (SPOS)**: Final frame changed from H (index 7) to L (index 11) → `SPOSL0`  
- **Imp (TROO)**: Final frame changed from H (index 7) to M (index 12) → `TROOM0`
- Dead bodies now stay in proper final position instead of looping back to falling animation

## Death Sequences

### Before Fix
```
Zombieman: POSSH1 → POSSI1 → POSSJ1 → POSSK1 → POSSH1 (loop back, sprites don't exist)
```

### After Fix
```
Zombieman: POSSH0 → POSSI0 → POSSJ0 → POSSK0 → POSSL0 (stays forever)
Shotgun Guy: SPOSH0 → SPOSI0 → SPOSJ0 → SPOSK0 → SPOSL0 (stays forever)
Imp: TROOH0 → TROOI0 → TROOJ0 → TROOK0 → TROOL0 → TROOM0 (stays forever)
```

## Files Modified
- `src/main/java/com/doomviewer/game/objects/StateDef.java`
- `src/main/java/com/doomviewer/game/objects/GameDefinitions.java`

## Testing
All generated sprite names now match existing sprites in WAD files:
- ✅ `BEXPE0` (barrel explosion)
- ✅ `POSSL0` (zombieman dead body)
- ✅ `SPOSL0` (shotgun guy dead body)
- ✅ `TROOM0` (imp dead body)

## Result
- ✅ Barrel explosions are now visible
- ✅ Enemy death animations are now visible
- ✅ Dead bodies remain visible with correct final frame
- ✅ No more disappearing enemies or barrels