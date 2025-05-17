package com.doomengine.services;

import com.doomengine.game.objects.MapObject;
import com.doomengine.game.objects.MobjType;
import com.doomengine.geometry.Vector2D;

import java.util.List;

public interface ObjectService {
    List<MapObject> getMapObjects();
    List<MapObject> getVisibleSortedMapObjects();
    com.doomengine.game.objects.Projectile createProjectile(MobjType type, Vector2D position, double angle, MapObject source);
    void removeObject(MapObject object);
}