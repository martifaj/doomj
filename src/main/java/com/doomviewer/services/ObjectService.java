package com.doomviewer.services;

import com.doomviewer.game.Player;
import com.doomviewer.game.objects.MapObject;
import com.doomviewer.game.objects.MobjType;
import com.doomviewer.misc.math.Vector2D;

import java.util.List;

public interface ObjectService {
    List<MapObject> getMapObjects();
    List<MapObject> getVisibleSortedMapObjects(Player player);
    com.doomviewer.game.objects.Projectile createProjectile(MobjType type, Vector2D position, double angle, MapObject source);
    void removeObject(MapObject object);
}