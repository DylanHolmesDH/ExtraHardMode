/*
 * This file is part of
 * ExtraHardMode Server Plugin for Minecraft
 *
 * Copyright (C) 2012 Ryan Hamshire
 * Copyright (C) 2013 Diemex
 *
 * ExtraHardMode is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * ExtraHardMode is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU Affero Public License
 * along with ExtraHardMode.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.extrahardmode.task;

import com.extrahardmode.ExtraHardMode;
import com.extrahardmode.config.RootConfig;
import com.extrahardmode.config.RootNode;
import com.extrahardmode.features.Feature;
import com.extrahardmode.module.DataStoreModule;
import com.extrahardmode.module.EntityModule;
import com.extrahardmode.module.PlayerModule;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.World.Environment;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.AbstractMap.SimpleEntry;
import java.util.List;

/**
 * Task to spawn more monsters.
 */
public class MoreMonstersTask implements Runnable
{

    //TODO Return to this and make it actually spawn and not just take the old locations
    //TODO if block not valid check random block nearby

    /**
     * Plugin instance.
     */
    private final ExtraHardMode plugin;
    /**
     * Config instanz
     */
    private final RootConfig CFG;
    /**
     * module to check if spawnlocation is safe etc.
     */
    private final EntityModule entityModule;
    private final PlayerModule playerModule;
    /**
     * Constructor.
     *
     * @param plugin - Plugin instance.
     */

    public MoreMonstersTask(ExtraHardMode plugin)
    {
        this.plugin = plugin;
        CFG = plugin.getModuleForClass(RootConfig.class);
        entityModule = plugin.getModuleForClass(EntityModule.class);
        playerModule = plugin.getModuleForClass(PlayerModule.class);
    }

    @Override
    public void run()
    {
        DataStoreModule dataStore = plugin.getModuleForClass(DataStoreModule.class);
        // spawn monsters from the last pass
        for (SimpleEntry<Player, Location> entry : dataStore.getPreviousLocations())
        {
            Player player = entry.getKey();
            Location location = entry.getValue();
            Chunk chunk = location.getChunk();
            World world = location.getWorld();

            try
            {
                location = verifyLocation(location);
                if (location != null && location.getChunk().isLoaded())
                {// spawn random monster(s)
                    if (world.getEnvironment() == Environment.NORMAL)
                    {
                        Entity mob = entityModule.spawnRandomMob(location);
                        //If there are Players nearby don't spawn
                        List<Entity> entities = mob.getNearbyEntities(16, 16, 16);
                        for (Entity ent : entities)
                            if (ent instanceof Player)
                            {
                                mob.remove();
                                break;
                            }
                    }
                }
            } catch (IllegalArgumentException ignored)
            {
            } // in case the player is in a different world from the saved location
        }

        // plan for the next pass
        dataStore.getPreviousLocations().clear();
        for (Player player : plugin.getServer().getOnlinePlayers())
        {
            Location verifiedLocation = null;
            //only if player hasn't got bypass and is in survival check location
            if (!playerModule.playerBypasses(player, Feature.MONSTERRULES))
                verifiedLocation = verifyLocation(player.getLocation());
            if (verifiedLocation != null)
                dataStore.getPreviousLocations().add(new SimpleEntry<Player, Location>(player, verifiedLocation));
        }
    }

    //TODO move this into a utility class
    /**
     * Tests if a a given location is elligible to be spawned on
     *
     * @return a valid Location or null if the location is invalid
     */
    private Location verifyLocation(Location location)
    {
        World world = location.getWorld();
        Location verifiedLoc = null;

        final int maxY = CFG.getInt(RootNode.MONSTER_SPAWNS_IN_LIGHT_MAX_Y, world.getName());

        // Only spawn monsters in normal world. End is crowded with endermen and nether is too extreme anyway, add config later
        int lightLvl = location.getBlock().getLightFromSky();
        if (world.getEnvironment() == World.Environment.NORMAL && (location.getY() < maxY && lightLvl < 3))
            verifiedLoc = entityModule.isLocSafeSpawn(location);

        return verifiedLoc;
    }
}