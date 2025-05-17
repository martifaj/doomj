package com.doomengine.game.objects;

import com.doomengine.game.ObjectManager;
import com.doomengine.services.AudioService;
import com.doomengine.services.CollisionService;
import com.doomengine.services.GameEngineTmp;
import com.doomengine.wad.assets.AssetData;
import com.doomengine.wad.datatypes.Thing;

public class GenericMapObject extends MapObject {
    public GenericMapObject(Thing thing, GameDefinitions gameDefinitions, AssetData assetData, CollisionService collisionService, AudioService audioService, GameEngineTmp engineTmp, ObjectManager objectManager) {
        super(thing, gameDefinitions, assetData, collisionService, audioService, engineTmp, objectManager);
    }
}
