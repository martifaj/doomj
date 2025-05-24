package com.doomviewer.game.objects;

import com.doomviewer.game.ObjectManager;
import com.doomviewer.services.AudioService;
import com.doomviewer.services.CollisionService;
import com.doomviewer.services.GameEngineTmp;
import com.doomviewer.wad.assets.AssetData;
import com.doomviewer.wad.datatypes.Thing;

public class GenericMapObject extends MapObject {
    public GenericMapObject(Thing thing, GameDefinitions gameDefinitions, AssetData assetData, CollisionService collisionService, AudioService audioService, GameEngineTmp engineTmp, ObjectManager objectManager) {
        super(thing, gameDefinitions, assetData, collisionService, audioService, engineTmp, objectManager);
    }
}
