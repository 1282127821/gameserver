package com.game.module.attach.arena;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import com.game.data.AwakeningSkillCfg;
import com.game.module.activity.ActivityConsts;
import com.game.module.activity.ActivityService;
import com.game.module.awakeningskill.AwakeningSkillService;
import com.game.module.goods.EquipService;
import com.google.common.collect.Lists;
import org.springframework.beans.factory.annotation.Autowired;

import com.game.data.Response;
import com.game.data.VIPConfig;
import com.game.module.goods.Goods;
import com.game.module.goods.GoodsService;
import com.game.module.log.LogConsume;
import com.game.module.player.Player;
import com.game.module.player.PlayerData;
import com.game.module.player.PlayerService;
import com.game.params.IntParam;
import com.game.params.Int2Param;
import com.game.params.ListParam;
import com.game.params.arena.ArenaFighterVO;
import com.game.params.arena.ArenaPlayerVO;
import com.game.params.arena.ArenaReportVO;
import com.game.params.arena.ArenaResultVO;
import com.game.params.arena.ArenaVO;
import com.game.util.ConfigData;
import com.game.util.RandomUtil;
import com.server.anotation.Command;
import com.server.anotation.Extension;
import com.server.util.GameData;
import com.server.util.ServerLogger;


@Extension
public class ArenaExtension {

    @Autowired
    private ArenaLogic logic;
    @Autowired
    private PlayerService playerService;
    @Autowired
    private GoodsService goodsService;
    @Autowired
    private EquipService equipService;
    @Autowired
    private AwakeningSkillService awakeningSkillService;
    @Autowired
    private ActivityService activityService;

    @Command(3801)
    public ArenaVO getInfo(int playerId, Object param) {
        ArenaAttach attach = logic.getAttach(playerId);
        ArenaPlayer aPlayer = logic.getArenaPlayerByUniqueId(attach.getUniqueId());
        if (aPlayer == null) {
            aPlayer = logic.searchArenaPlayer(playerId);
            attach.setUniqueId(aPlayer.getUniqueId());
        }
        ArenaVO vo = new ArenaVO();
        vo.rank = aPlayer.getRank();
        vo.uniqueId = attach.getUniqueId();
        vo.buyTime = attach.getBuyCount();
        vo.record = attach.getRecord();
        vo.challenge = attach.getChallenge();
        attach.getPage().set(0);
        return vo;
    }

    @Command(3802)
    public ListParam<ArenaPlayerVO> getOpponentList(int playerId, IntParam param) {
//        logic.repair();
        ArenaAttach attach = logic.getAttach(playerId);
        ArenaPlayer me = logic.getArenaPlayerByUniqueId(attach.getUniqueId());
        if (me == null) {
            ArenaPlayer arenaPlayer = logic.searchArenaPlayer(playerId);
            me = logic.getArenaPlayerByUniqueId(arenaPlayer.getUniqueId());
            attach.setUniqueId(arenaPlayer.getUniqueId());
        }
        int minRank = logic.getMinRank();
        int meRank = me.getRank();
        Set<Integer> ranks = new HashSet<>(4);
        if (meRank < 7) {
            for (int i = 1; i < 7; i++) {
                if (i != meRank) {
                    ranks.add(i);
                }
            }
        } else {
            int min;
            int max;
            int delta = Math.round(meRank * 0.5f);
            if (delta < 3) {
                delta = 4;
            }

            if (meRank != minRank) { //随机比自己低的排名角色
                min = Math.min(meRank + delta, minRank);
                max = meRank + 1;
                int rank = RandomUtil.randInt(max, min);
                ranks.add(rank);
            }

            min = meRank - 1;
            max = meRank - delta;

            List<Integer> rankList = new ArrayList<>();
            for (int i = max; i <= min; i++) {
                rankList.add(i);
            }
            int k = 4 - ranks.size();
            for (int i = 0; i < k; i++) { //随机比自己排名高的角色
                int index = RandomUtil.randInt(rankList.size());
                ranks.add(rankList.remove(index));
            }
            rankList.clear();
            rankList = null;
        }

        ListParam<ArenaPlayerVO> result = new ListParam<>();
        result.code = param.param;
        List<ArenaPlayerVO> list = new ArrayList<>();
        for (int rank : ranks) {
            ArenaPlayer aPlayer = logic.getArenaPlayerByRank(rank);
            Player player = playerService.getPlayer(aPlayer.getPlayerId());
            if (player == null) {
                ServerLogger.warn(rank + " not found player,id=" + aPlayer.getPlayerId());
                continue;
            }
            ArenaPlayerVO vo = new ArenaPlayerVO();
            vo.rank = rank;
            vo.uniqueId = aPlayer.getUniqueId();
            vo.name = player.getName();
            vo.level = player.getLev();
            vo.fightingValue = player.getFight();
            vo.vocation = player.getVocation();
            vo.fashionId = player.getFashionId();
            vo.weapon = player.getWeaponId();
            vo.head = playerService.getPlayerData(player.getPlayerId()).getCurHead();
            vo.id = player.getPlayerId();
            list.add(vo);
        }
        result.params = list;
        return result;
    }

    @Command(3803)
    public ArenaFighterVO challenge(int playerId, Int2Param param) {
        ArenaFighterVO vo = new ArenaFighterVO();
        ArenaPlayerVO playerVo = new ArenaPlayerVO();
        ArenaAttach attach = logic.getAttach(playerId);
        if (param.param1 == attach.getUniqueId()) {
            vo.code = Response.ERR_PARAM;
            return vo;
        }

        if (attach.getChallenge() <= 0) {
            vo.code = Response.ARENA_NO_CHELLENGE;
            return vo;
        }

        if (attach.getOpponent() > 0) {
            vo.code = Response.ERR_PARAM;
            return vo;
        }

        ArenaPlayer opponent = logic.getArenaPlayerByUniqueId(param.param1);
        if (opponent == null) {
            vo.code = Response.ERR_PARAM;
            return vo;
        }

        attach.alterChallenge(-1);
        attach.setOpponent(opponent.getUniqueId());
        attach.setIsRevenge(param.param2 > 0);
        attach.commitSync();
        Player player = playerService.getPlayer(opponent.getPlayerId());
        PlayerData playerData = playerService.getPlayerData(opponent.getPlayerId());

        playerVo.rank = opponent.getRank();
        playerVo.uniqueId = opponent.getUniqueId();
        playerVo.name = player.getName();
        playerVo.level = player.getLev();
        playerVo.fightingValue = player.getFight();
        playerVo.vocation = player.getVocation();
        playerVo.fashionId = player.getFashionId();
        playerVo.weapon = player.getWeaponId();
        playerVo.head = playerService.getPlayerData(player.getPlayerId()).getCurHead();
        playerVo.id = player.getPlayerId();

        vo.uniqueId = param.param1;
        vo.attack = player.getAttack();
        vo.defense = player.getDefense();
        vo.crit = player.getCrit();
        vo.symptom = player.getSymptom();
        vo.fu = player.getFu();
        vo.hp = player.getHp();
        vo.curCards = playerData.getCurrCardIds();
        vo.curSkills = playerData.getCurSkills();
        vo.lv = player.getLev();
        vo.name = player.getName();
        vo.vocation = player.getVocation();
        List<Integer> bufferList = equipService.getBufferList(playerId);
        vo.bufferList = Lists.newArrayList(bufferList);
        vo.playerInfo = playerVo;
        List<Int2Param> awakeningSkillList = awakeningSkillService.getAwakeningSkillList(opponent.getPlayerId());
        if (awakeningSkillList != null && awakeningSkillList.size() > 0) {
            vo.awakenSkillList = awakeningSkillList;
        }
        return vo;
    }

    @Command(3804)
    public ArenaResultVO takeResult(int playerId, IntParam param) {
        return logic.takeResult(playerId, param.param > 0);
    }

    //购买挑战次数
    @Command(3805)
    public IntParam buyChallenge(int playerId, IntParam param) {
        ArenaAttach attach = logic.getAttach(playerId);
        Player player = playerService.getPlayer(playerId);
        VIPConfig vip = GameData.getConfig(VIPConfig.class, player.getVip());
        IntParam result = new IntParam();
        int count = attach.getBuyCount();
        int buyCount = param.param;
        if (count + buyCount > vip.arenaChallenge) {
            result.param = Response.ARENA_NO_BUY;
            return result;
        }
        int price = 0;
        for (int[] arr : ConfigData.globalParam().arenaBuyChallenge) {
            if (buyCount < arr[1]) {
                int _buyCount = 0;
                _buyCount = Math.min(buyCount, arr[1] - count);

                price += _buyCount * arr[2];
                buyCount -= _buyCount;
                count += _buyCount;
            }
        }

        result.param = goodsService.decConsume(playerId, new int[][]{{Goods.DIAMOND, price}}, LogConsume.BUY_ARENA_CHALLENGE);
        if (result.param == Response.SUCCESS) {
            attach.alterChallenge(param.param);
            attach.alterBuyCount(param.param);
            attach.commitSync();

            //好友活动
            activityService.completionCumulative(playerId, ActivityConsts.ActivityTaskCondType.T_ARENA_CHALLENGE, 1);
        }
        return result;
    }

    //强退
    @Command(3806)
    public IntParam quit(int playerId, Object param) {
        logic.quit(playerId);
        return new IntParam();
    }

    //战报
    public static final int REPORT = 3807;

    @Command(3807)
    public ListParam<ArenaReportVO> getReport(int playerId, Object param) {
        ArenaAttach attach = logic.getAttach(playerId);
        List<ArenaReportVO> reports = attach.getReport();
        if (reports.isEmpty()) {
            return null;
        }
        ListParam<ArenaReportVO> msg = new ListParam<ArenaReportVO>();
        msg.params = new ArrayList<>(reports);
        return msg;
    }
}
