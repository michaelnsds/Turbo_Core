/*
 * Copyright (C) 2004-2015 L2J Server
 * 
 * This file is part of L2J Server.
 * 
 * L2J Server is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * L2J Server is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package l2r.gameserver.communitybbs.SunriseBoards.dropCalc;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import l2r.Config;
import l2r.L2DatabaseFactory;
import l2r.gameserver.data.sql.NpcTable;
import l2r.gameserver.data.xml.impl.ItemData;
import l2r.gameserver.model.actor.templates.L2NpcTemplate;
import l2r.gameserver.model.items.L2Item;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author vGodFather
 */
public class DropInfoHandler
{
	private static final Logger _log = LoggerFactory.getLogger(DropInfoHandler.class);
	
	private static final String CUSTOM_SELECT_DROPLIST_ALL = "SELECT * FROM custom_droplist ORDER BY mobId, chance DESC";
	private static final String SELECT_DROPLIST_ALL = "SELECT * FROM droplist ORDER BY mobId, chance DESC";
	
	private final Map<Integer, ArrayList<DropInfoHolder>> allItemDropIndex = new HashMap<>();
	private final Map<Integer, String> itemNameMap = new HashMap<>();
	public static final Set<Integer> HERBS = new HashSet<>();
	
	public DropInfoHandler()
	{
	
	}
	
	public void load()
	{
		loadHerbList();
		
		try (Connection con = L2DatabaseFactory.getInstance().getConnection())
		{
			loadNpcsDrop(con, false);
			if (Config.CUSTOM_DROPLIST_TABLE)
			{
				loadNpcsDrop(con, true);
			}
		}
		catch (Exception e)
		{
			_log.error(getClass().getSimpleName() + ": Error reading NPC DROP Data: " + e.getMessage(), e);
		}
		_log.info(getClass().getSimpleName() + ": Loaded " + allItemDropIndex.size() + " drop data for calculator.");
	}
	
	private static void loadHerbList()
	{
		HERBS.addAll(new Range(8154, 8158).values()); // HERBs
		HERBS.addAll(new Range(8600, 8615).values()); // HERBs
		HERBS.addAll(new Range(8952, 8954).values()); // HERBs
		HERBS.addAll(new Range(10655, 10658).values()); // HERBs
		HERBS.addAll(new Range(10655, 10658).values()); // HERBs
		HERBS.add(13028);// Vitality Replenishing Herb
		HERBS.addAll(new Range(10432, 10434).values());// Kertin's Herb
	}
	
	public void addDropInfo(int itemId, DropInfoHolder drop)
	{
		ArrayList<DropInfoHolder> list;
		if ((list = getDrop(itemId)) == null)
		{
			list = new ArrayList<>();
			allItemDropIndex.put(itemId, list);
		}
		
		if (!list.contains(drop))
		{
			list.add(drop);
		}
	}
	
	public void loadNpcsDrop(Connection con, boolean isCustom)
	{
		final String query = isCustom ? CUSTOM_SELECT_DROPLIST_ALL : SELECT_DROPLIST_ALL;
		try (PreparedStatement ps = con.prepareStatement(query))
		{
			try (ResultSet rs = ps.executeQuery())
			{
				L2NpcTemplate npcDat = null;
				while (rs.next())
				{
					int mobId = rs.getInt("mobId");
					npcDat = NpcTable.getInstance().getTemplate(mobId);
					if ((npcDat == null) || DropCalculatorConfigs.RESTRICTED_MOB_DROPLIST_IDS.contains(mobId))
					{
						_log.warn(getClass().getSimpleName() + ": Drop data for undefined NPC. npcId: " + mobId);
						continue;
					}
					
					int itemId = rs.getInt("itemId");
					if (!HERBS.contains(itemId))
					{
						addDropInfo(itemId, new DropInfoHolder(mobId, npcDat.getName(), npcDat.getLevel(), rs.getInt("min"), rs.getInt("max"), Math.min(100, rs.getInt("chance") / 10000), rs.getInt("category") < 0));
					}
				}
			}
		}
		catch (Exception e)
		{
			_log.error(getClass().getSimpleName() + ": Error reading NPC dropdata. ", e);
		}
		
		for (L2Item item : ItemData.getInstance().getAllTemItems())
		{
			if (item != null)
			{
				itemNameMap.put(item.getId(), item.getName());
			}
		}
		
		allItemDropIndex.values().forEach(list -> list.sort((o1, o2) -> NpcTable.getInstance().getTemplate(o1.getNpcId()).getLevel() - NpcTable.getInstance().getTemplate(o2.getNpcId()).getLevel()));
	}
	
	public ArrayList<DropInfoHolder> getDrop(int itemId)
	{
		return allItemDropIndex.get(itemId);
	}
	
	public Map<Integer, ArrayList<DropInfoHolder>> getInfo()
	{
		return allItemDropIndex;
	}
	
	public Comparator<DropInfoHolder> compareByChances = (o1, o2) ->
	{
		double level1 = o1.getChance();
		double level2 = o2.getChance();
		
		return level1 < level2 ? 1 : level1 == level2 ? 0 : -1;
	};
	
	public static DropInfoHandler getInstance()
	{
		return SingletonHolder._instance;
	}
	
	private static class SingletonHolder
	{
		protected static final DropInfoHandler _instance = new DropInfoHandler();
	}
}
