package dev.laakirun.veyloria.server.game;

import dev.laakirun.veyloria.common.entity.NpcEntity;
import dev.laakirun.veyloria.server.VeyloriaServerRuntime;
import dev.laakirun.veyloria.server.auth.AuthService;
import dev.laakirun.veyloria.server.content.MobTemplate;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.target.TargetGoal;
import net.minecraft.world.entity.animal.Bee;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.ZombifiedPiglin;
import net.minecraft.world.entity.monster.piglin.Piglin;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class CommonMobAiService {
    public static final String ALLIED_ATTACK_BLOCK_MESSAGE = "Союзных существ атаковать нельзя";

    private static final Logger SPAWN_LOGGER = LoggerFactory.getLogger("veyloria.spawn");
    private static final String TAG_PATCHED = "veyloria_common_ai_patched";
    private static final String TAG_RANK = "veyloria_common_ai_rank";
    private static final String TAG_DISPOSITION = "veyloria_common_ai_disposition";
    private static final String TAG_HOME_X = "veyloria_common_ai_home_x";
    private static final String TAG_HOME_Y = "veyloria_common_ai_home_y";
    private static final String TAG_HOME_Z = "veyloria_common_ai_home_z";
    private static final String TAG_GROUP_ID = "veyloria_common_ai_group_id";
    private static final String TAG_LEADER_ID = "veyloria_common_ai_leader_id";
    private static final String TAG_SPAWN_TICK = "veyloria_common_ai_spawn_tick";
    private static final String TAG_EVADING = "veyloria_common_ai_evading";
    private static final String TAG_EVADE_STARTED_AT = "veyloria_common_ai_evade_started_at";
    private static final String TAG_CANNOT_REACH = "veyloria_common_ai_cannot_reach";
    private static final String TAG_LAST_COMBAT_TICK = "veyloria_common_ai_last_combat";
    private static final String TAG_AGGRO_UNTIL = "veyloria_common_ai_aggro_until";
    private static final String TAG_AGGRO_TARGET_UUID = "veyloria_common_ai_aggro_target_uuid";
    private static final String TAG_ALLIED_PROVOKED_BY_PLAYER_UNTIL = "veyloria_common_ai_allied_provoked_by_player_until";
    private static final String TAG_NEUTRAL_ATTACKED_PLAYER_UNTIL = "veyloria_common_ai_neutral_attacked_player_until";
    private static final String TAG_NEUTRAL_ATTACKED_HOSTILE_UNTIL = "veyloria_common_ai_neutral_attacked_hostile_until";
    private static final String TAG_NEUTRAL_PROVOKED_BY_PLAYER_UNTIL = "veyloria_common_ai_neutral_provoked_by_player_until";
    private static final String TAG_NEUTRAL_ATTACKED_BY_ALLIED_UNTIL = "veyloria_common_ai_neutral_attacked_by_allied_until";
    private static final String TAG_NEUTRAL_ATTACKED_BY_HOSTILE_UNTIL = "veyloria_common_ai_neutral_attacked_by_hostile_until";
    private static final String TAG_FALLBACK_ATTACK_DAMAGE = "veyloria_common_ai_fallback_attack_damage";
    private static final String TAG_MARK_ALLIED = "veyloria_allied";
    private static final String TAG_MARK_NEUTRAL = "veyloria_neutral";
    private static final String TAG_MARK_HOSTILE = "veyloria_hostile";
    private static final String TAG_MARK_ELITE = "veyloria_elite";
    private static final String TAG_MARK_BOSS = "veyloria_boss";

    public void patchMob(ServerLevel level, Mob mob, long gameTime) {
        if (level == null || mob == null || mob.isNoAi() || !(mob instanceof PathfinderMob pathfinderMob)) {
            return;
        }
        MobRank rank = resolveRank(mob);
        if (rank == MobRank.BOSS) {
            return;
        }
        MobDisposition disposition = resolveDisposition(mob);
        CompoundTag data = mob.getPersistentData();
        if (!data.getBoolean(TAG_PATCHED)) {
            data.putBoolean(TAG_PATCHED, true);
            data.putString(TAG_RANK, rank.id());
            data.putString(TAG_DISPOSITION, disposition.id());
            data.putLong(TAG_SPAWN_TICK, gameTime);
            setHomeIfMissing(mob);
            assignGroup(level, mob, gameTime);
            SPAWN_LOGGER.debug("Patched common ai for mob {} rank={} disposition={}", mob.getUUID(), rank.id(), disposition.id());
        } else {
            if (!data.contains(TAG_RANK)) {
                data.putString(TAG_RANK, rank.id());
            }
            if (!data.contains(TAG_DISPOSITION)) {
                data.putString(TAG_DISPOSITION, disposition.id());
            }
            setHomeIfMissing(mob);
            assignGroup(level, mob, gameTime);
        }
        ensureFallbackAttackDamage(mob);
        installGoals(pathfinderMob);
    }

    public boolean isCommonOrElite(LivingEntity entity) {
        if (!(entity instanceof Mob mob) || mob.isNoAi() || mob instanceof NpcEntity) {
            return false;
        }
        MobRank rank = resolveRank(mob);
        return rank == MobRank.COMMON || rank == MobRank.ELITE;
    }

    public boolean isAllied(Mob mob) {
        return mob != null && resolveDisposition(mob) == MobDisposition.ALLIED;
    }

    public String dispositionId(LivingEntity entity) {
        MobDisposition disposition = resolveDisposition(entity);
        return disposition == null ? "" : disposition.id();
    }

    public boolean isAttackableByPlayer(Mob mob, long gameTime) {
        return mob != null && resolveDisposition(mob) != MobDisposition.ALLIED && !isEvading(mob, gameTime);
    }

    public void alertGroupOnPlayerAttack(Mob targetMob, ServerPlayer player, long gameTime) {
        if (targetMob == null || player == null || !isCommonOrElite(targetMob) || !(targetMob.level() instanceof ServerLevel level)) {
            return;
        }
        MobDisposition targetDisposition = resolveDisposition(targetMob);
        if (targetDisposition != MobDisposition.ALLIED && targetDisposition != MobDisposition.NEUTRAL) {
            return;
        }
        UUID gid = groupId(targetMob);
        if (gid == null) {
            applyPlayerAggro(targetMob, player, gameTime);
            return;
        }
        for (Mob member : groupMembers(level, targetMob, gid, 96.0D)) {
            applyPlayerAggro(member, player, gameTime);
        }
        applyPlayerAggro(targetMob, player, gameTime);
    }

    public DamageDecision evaluateDamage(LivingEntity source, LivingEntity target, long gameTime) {
        if (source == null || target == null) {
            return DamageDecision.allow();
        }
        boolean sourceHandled = isCommonOrElite(source);
        boolean targetHandled = isCommonOrElite(target);
        if (!sourceHandled && !targetHandled) {
            return DamageDecision.allow();
        }
        if (target instanceof Mob targetMob && targetHandled && isEvading(targetMob, gameTime)) {
            return DamageDecision.deny(null);
        }
        if (source instanceof ServerPlayer && target instanceof Mob targetMob && targetHandled) {
            if (!isAttackableByPlayer(targetMob, gameTime)) {
                String message = resolveDisposition(targetMob) == MobDisposition.ALLIED ? ALLIED_ATTACK_BLOCK_MESSAGE : null;
                return DamageDecision.deny(message);
            }
            return DamageDecision.allow();
        }
        if (source instanceof Mob sourceMob && sourceHandled && !canTarget(sourceMob, target, gameTime)) {
            return DamageDecision.deny(null);
        }
        return DamageDecision.allow();
    }

    public void recordSuccessfulDamage(LivingEntity attacker, LivingEntity target, long gameTime) {
        if (attacker instanceof Mob attackerMob && isCommonOrElite(attackerMob)) {
            engageTarget(attackerMob, target, gameTime);
        }
        if (target instanceof Mob targetMob && isCommonOrElite(targetMob)) {
            touchAggro(targetMob, attacker, gameTime);
        }
        if (attacker instanceof Mob attackerMob && isCommonOrElite(attackerMob) && resolveDisposition(attackerMob) == MobDisposition.NEUTRAL) {
            if (target instanceof ServerPlayer) {
                markRecent(attackerMob, TAG_NEUTRAL_ATTACKED_PLAYER_UNTIL, gameTime);
            } else if (target instanceof Mob targetMob && resolveDisposition(targetMob) == MobDisposition.HOSTILE) {
                markRecent(attackerMob, TAG_NEUTRAL_ATTACKED_HOSTILE_UNTIL, gameTime);
            }
        }
        if (target instanceof Mob targetMob && isCommonOrElite(targetMob) && resolveDisposition(targetMob) == MobDisposition.NEUTRAL) {
            if (attacker instanceof ServerPlayer player) {
                markRecent(targetMob, TAG_NEUTRAL_PROVOKED_BY_PLAYER_UNTIL, gameTime);
                alertGroupOnPlayerAttack(targetMob, player, gameTime);
                engageTarget(targetMob, player, gameTime);
            } else if (attacker instanceof Mob attackerMob) {
                MobDisposition sourceDisposition = resolveDisposition(attackerMob);
                if (sourceDisposition == MobDisposition.ALLIED) {
                    markRecent(targetMob, TAG_NEUTRAL_ATTACKED_BY_ALLIED_UNTIL, gameTime);
                } else if (sourceDisposition == MobDisposition.HOSTILE) {
                    markRecent(targetMob, TAG_NEUTRAL_ATTACKED_BY_HOSTILE_UNTIL, gameTime);
                }
            }
        }
        if (attacker instanceof Mob attackerMob && isCommonOrElite(attackerMob) && !isEvading(attackerMob, gameTime)
            && canTarget(attackerMob, target, gameTime)) {
            engageTarget(attackerMob, target, gameTime);
        }
    }

    public boolean canTarget(LivingEntity attacker, LivingEntity candidate, long gameTime) {
        if (attacker == null || candidate == null || attacker == candidate || !attacker.isAlive() || !candidate.isAlive()) {
            return false;
        }
        if (attacker instanceof Mob attackerMob && isEvading(attackerMob, gameTime)) {
            return false;
        }
        if (candidate instanceof Mob candidateMob && isEvading(candidateMob, gameTime)) {
            return false;
        }
        MobDisposition attackerDisposition = resolveDisposition(attacker);
        if (attackerDisposition == null) {
            return false;
        }
        if (candidate instanceof ServerPlayer playerCandidate) {
            if (!isAuthenticated(playerCandidate)) {
                return false;
            }
            return switch (attackerDisposition) {
                case ALLIED -> attacker instanceof Mob alliedMob && alliedProvokedByPlayer(alliedMob, gameTime);
                case NEUTRAL -> attacker instanceof Mob neutralMob && neutralProvokedByPlayer(neutralMob, gameTime);
                case HOSTILE -> true;
            };
        }
        if (!(candidate instanceof Mob candidateMob)) {
            return false;
        }
        MobDisposition candidateDisposition = resolveDisposition(candidateMob);
        if (candidateDisposition == null) {
            return false;
        }
        return switch (attackerDisposition) {
            case ALLIED -> candidateDisposition == MobDisposition.HOSTILE
                || (candidateDisposition == MobDisposition.NEUTRAL && neutralAttackedPlayerRecently(candidateMob, gameTime));
            case NEUTRAL -> switch (candidateDisposition) {
                case ALLIED -> attacker instanceof Mob neutralMob && neutralAttackedByAlliedRecently(neutralMob, gameTime);
                case HOSTILE -> attacker instanceof Mob neutralMob && neutralAttackedByHostileRecently(neutralMob, gameTime);
                case NEUTRAL -> false;
            };
            case HOSTILE -> candidateDisposition == MobDisposition.ALLIED
                || (candidateDisposition == MobDisposition.NEUTRAL && neutralAttackedHostileRecently(candidateMob, gameTime));
        };
    }

    public boolean isEvading(Mob mob, long gameTime) {
        if (mob == null || !isCommonOrElite(mob)) {
            return false;
        }
        return mob.getPersistentData().getBoolean(TAG_EVADING);
    }

    public boolean isAggroed(Mob mob, long gameTime) {
        if (mob == null || !isCommonOrElite(mob)) {
            return false;
        }
        return mob.getPersistentData().getLong(TAG_AGGRO_UNTIL) >= gameTime;
    }

    public boolean shouldStartEvade(PathfinderMob mob, long gameTime) {
        if (isEvading(mob, gameTime)) {
            return false;
        }
        if (isAggroed(mob, gameTime) && (mob.getTarget() == null || !mob.getTarget().isAlive())) {
            return false;
        }
        if (mob.getPersistentData().getInt(TAG_CANNOT_REACH) >= CommonMobAiSettings.CANNOT_REACH_TICKS) {
            return true;
        }
        if (distanceToHome(mob) <= CommonMobAiSettings.COMBAT_LEASH_RADIUS) {
            return false;
        }
        return wasInCombatRecently(mob, gameTime);
    }

    public void startEvade(PathfinderMob mob, long gameTime) {
        CompoundTag data = mob.getPersistentData();
        data.putBoolean(TAG_EVADING, true);
        data.putLong(TAG_EVADE_STARTED_AT, gameTime);
        data.putInt(TAG_CANNOT_REACH, 0);
        data.putLong(TAG_LAST_COMBAT_TICK, gameTime);
        clearAggro(mob);
        mob.setTarget(null);
        mob.getNavigation().stop();
    }

    public void tickEvade(PathfinderMob mob, long gameTime) {
        mob.setTarget(null);
        healForEvade(mob, gameTime);
        BlockPos home = homePos(mob);
        mob.getNavigation().moveTo(home.getX() + 0.5D, home.getY(), home.getZ() + 0.5D, 1.18D);
    }

    public void stopEvade(PathfinderMob mob, long gameTime) {
        CompoundTag data = mob.getPersistentData();
        data.putBoolean(TAG_EVADING, false);
        data.remove(TAG_EVADE_STARTED_AT);
        data.putInt(TAG_CANNOT_REACH, 0);
        data.putLong(TAG_LAST_COMBAT_TICK, gameTime);
        clearAggro(mob);
        mob.setTarget(null);
        mob.getNavigation().stop();
    }

    public boolean hasReturnedHome(PathfinderMob mob) {
        return distanceToHome(mob) <= CommonMobAiSettings.RETURN_STOP_RADIUS;
    }

    public boolean shouldFinishEvade(PathfinderMob mob) {
        return hasReturnedHome(mob) && mob.getHealth() >= mob.getMaxHealth() - 0.01F;
    }

    public boolean isReachableForAttack(Mob mob, LivingEntity target) {
        if (mob == null || target == null || !target.isAlive()) {
            return false;
        }
        if (!mob.hasLineOfSight(target)) {
            return false;
        }
        return mob.distanceTo(target) <= meleeReach(mob, target);
    }

    public Vec3 cohesionTarget(PathfinderMob mob) {
        BlockPos home = homePos(mob);
        Vec3 fallback = Vec3.atBottomCenterOf(home);
        UUID groupId = groupId(mob);
        if (groupId == null || !(mob.level() instanceof ServerLevel level)) {
            return fallback;
        }
        List<Mob> members = groupMembers(level, mob, groupId, CommonMobAiSettings.GROUP_RADIUS * 2.0D);
        if (members.isEmpty()) {
            return fallback;
        }
        LivingEntity leader = resolveLeader(level, mob, groupId);
        double sx = mob.getX();
        double sy = mob.getY();
        double sz = mob.getZ();
        int count = 1;
        for (Mob member : members) {
            sx += member.getX();
            sy += member.getY();
            sz += member.getZ();
            count++;
        }
        Vec3 center = new Vec3(sx / count, sy / count, sz / count);
        if (leader != null && leader.isAlive()) {
            center = center.scale(0.7D).add(leader.position().scale(0.3D));
        }
        Vec3 toCenter = center.subtract(mob.position());
        double horizontalDistance = Math.sqrt(toCenter.x * toCenter.x + toCenter.z * toCenter.z);
        if (horizontalDistance <= CommonMobAiSettings.GROUP_RADIUS * 1.35D) {
            return fallback;
        }
        if (horizontalDistance > 0.0001D) {
            double maxStep = 6.0D;
            double step = Math.min(maxStep, horizontalDistance);
            Vec3 stepPoint = mob.position().add(toCenter.normalize().scale(step));
            return clampToHome(mob, stepPoint);
        }
        return clampToHome(mob, center);
    }

    public Vec3 randomHomePoint(PathfinderMob mob) {
        if (!(mob.level() instanceof ServerLevel level)) {
            return null;
        }
        BlockPos home = homePos(mob);
        for (int i = 0; i < 8; i++) {
            double angle = mob.getRandom().nextDouble() * Math.PI * 2.0D;
            double distance = mob.getRandom().nextDouble() * CommonMobAiSettings.HOME_WANDER_RADIUS;
            double x = home.getX() + 0.5D + Math.cos(angle) * distance;
            double z = home.getZ() + 0.5D + Math.sin(angle) * distance;
            BlockPos yPos = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, BlockPos.containing(x, home.getY(), z));
            Vec3 point = new Vec3(yPos.getX() + 0.5D, yPos.getY(), yPos.getZ() + 0.5D);
            if (distanceToHome(point, home) <= CommonMobAiSettings.HOME_WANDER_RADIUS + 0.5D) {
                return point;
            }
        }
        return Vec3.atBottomCenterOf(home);
    }

    public LivingEntity findAssistTarget(PathfinderMob mob, long gameTime) {
        if (!(mob.level() instanceof ServerLevel level) || isEvading(mob, gameTime)) {
            return null;
        }
        double radius = Math.max(CommonMobAiSettings.GROUP_RADIUS, targetSearchRadius(mob));
        AABB area = mob.getBoundingBox().inflate(radius);
        LivingEntity selected = null;
        double best = Double.MAX_VALUE;
        for (ServerPlayer player : level.getEntitiesOfClass(ServerPlayer.class, area, ServerPlayer::isAlive)) {
            LivingEntity aggressor = player.getLastHurtByMob();
            if (aggressor == null || !aggressor.isAlive() || !canTarget(mob, aggressor, gameTime)) {
                continue;
            }
            double distance = mob.distanceToSqr(aggressor);
            if (distance < best) {
                best = distance;
                selected = aggressor;
            }
        }
        for (Mob ally : level.getEntitiesOfClass(Mob.class, area, entity -> entity.isAlive() && !entity.getUUID().equals(mob.getUUID()))) {
            if (!canAssistFrom(mob, ally)) {
                continue;
            }
            LivingEntity aggressor = ally.getTarget();
            if (aggressor == null || !aggressor.isAlive()) {
                aggressor = ally.getLastHurtByMob();
            }
            if (aggressor == null || !aggressor.isAlive() || !canTarget(mob, aggressor, gameTime)) {
                continue;
            }
            double distance = mob.distanceToSqr(aggressor);
            if (distance < best) {
                best = distance;
                selected = aggressor;
            }
        }
        return selected;
    }

    public LivingEntity findAcquireTarget(PathfinderMob mob, long gameTime) {
        if (!(mob.level() instanceof ServerLevel level) || isEvading(mob, gameTime)) {
            return null;
        }
        double radius = targetSearchRadius(mob);
        AABB area = mob.getBoundingBox().inflate(radius);
        LivingEntity selected = null;
        double best = Double.MAX_VALUE;
        for (ServerPlayer player : level.getEntitiesOfClass(ServerPlayer.class, area, ServerPlayer::isAlive)) {
            if (!canTarget(mob, player, gameTime)) {
                continue;
            }
            double distance = mob.distanceToSqr(player);
            if (distance < best) {
                best = distance;
                selected = player;
            }
        }
        for (Mob candidate : level.getEntitiesOfClass(Mob.class, area, entity -> entity.isAlive() && !entity.getUUID().equals(mob.getUUID()))) {
            if (!canTarget(mob, candidate, gameTime)) {
                continue;
            }
            double distance = mob.distanceToSqr(candidate);
            if (distance < best) {
                best = distance;
                selected = candidate;
            }
        }
        return selected;
    }

    private void installGoals(PathfinderMob mob) {
        mob.goalSelector.getAvailableGoals().removeIf(wrapped -> true);
        mob.targetSelector.getAvailableGoals().removeIf(wrapped -> true);
        mob.goalSelector.addGoal(0, new EvadeReturnHomeGoal(this, mob));
        mob.goalSelector.addGoal(1, new FloatGoal(mob));
        mob.goalSelector.addGoal(2, new CannotReachTrackerGoal(this, mob));
        mob.goalSelector.addGoal(3, new AggroRecoverGoal(this, mob));
        mob.goalSelector.addGoal(4, new CommonMeleeAttackGoal(this, mob, 1.10D));
        mob.goalSelector.addGoal(5, new GroupCohesionGoal(this, mob, 0.92D));
        mob.goalSelector.addGoal(7, new WanderWithinHomeGoal(this, mob, 0.95D));
        mob.goalSelector.addGoal(8, new LookAtPlayerGoal(mob, Player.class, 8.0F));
        mob.goalSelector.addGoal(9, new RandomLookAroundGoal(mob));
        mob.targetSelector.addGoal(1, new HurtByCommonGoal(this, mob));
        mob.targetSelector.addGoal(2, new AssistAllyGoal(this, mob));
        mob.targetSelector.addGoal(3, new AcquireTargetGoal(this, mob));
    }

    private void setHomeIfMissing(Mob mob) {
        CompoundTag data = mob.getPersistentData();
        if (data.contains(TAG_HOME_X) && data.contains(TAG_HOME_Y) && data.contains(TAG_HOME_Z)) {
            return;
        }
        BlockPos pos = mob.blockPosition();
        data.putInt(TAG_HOME_X, pos.getX());
        data.putInt(TAG_HOME_Y, pos.getY());
        data.putInt(TAG_HOME_Z, pos.getZ());
    }

    private BlockPos homePos(Mob mob) {
        CompoundTag data = mob.getPersistentData();
        if (!data.contains(TAG_HOME_X) || !data.contains(TAG_HOME_Y) || !data.contains(TAG_HOME_Z)) {
            setHomeIfMissing(mob);
        }
        return new BlockPos(data.getInt(TAG_HOME_X), data.getInt(TAG_HOME_Y), data.getInt(TAG_HOME_Z));
    }

    private double distanceToHome(Mob mob) {
        return distanceToHome(mob.position(), homePos(mob));
    }

    private static double distanceToHome(Vec3 point, BlockPos home) {
        return point.distanceTo(Vec3.atBottomCenterOf(home));
    }

    private Vec3 clampToHome(Mob mob, Vec3 point) {
        Vec3 home = Vec3.atBottomCenterOf(homePos(mob));
        Vec3 delta = point.subtract(home);
        double horizontal = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
        if (horizontal <= CommonMobAiSettings.HOME_WANDER_RADIUS) {
            return point;
        }
        if (horizontal <= 0.0001D) {
            return home;
        }
        double scale = CommonMobAiSettings.HOME_WANDER_RADIUS / horizontal;
        return new Vec3(home.x + delta.x * scale, point.y, home.z + delta.z * scale);
    }

    private void assignGroup(ServerLevel level, Mob mob, long gameTime) {
        CompoundTag data = mob.getPersistentData();
        if (!data.hasUUID(TAG_GROUP_ID)) {
            Optional<Mob> nearby = findNearbyGroupMember(level, mob);
            UUID groupId = nearby.map(candidate -> candidate.getPersistentData().getUUID(TAG_GROUP_ID)).orElseGet(UUID::randomUUID);
            data.putUUID(TAG_GROUP_ID, groupId);
            data.putLong(TAG_SPAWN_TICK, gameTime);
            UUID leader = nearby
                .map(candidate -> candidate.getPersistentData().hasUUID(TAG_LEADER_ID)
                    ? candidate.getPersistentData().getUUID(TAG_LEADER_ID)
                    : candidate.getUUID())
                .orElse(mob.getUUID());
            data.putUUID(TAG_LEADER_ID, leader);
            return;
        }
        if (!data.hasUUID(TAG_LEADER_ID)) {
            data.putUUID(TAG_LEADER_ID, mob.getUUID());
        }
        if (!data.contains(TAG_SPAWN_TICK)) {
            data.putLong(TAG_SPAWN_TICK, gameTime);
        }
    }

    private Optional<Mob> findNearbyGroupMember(ServerLevel level, Mob mob) {
        AABB area = mob.getBoundingBox().inflate(CommonMobAiSettings.GROUP_RADIUS);
        Mob best = null;
        double bestDistance = Double.MAX_VALUE;
        for (Mob candidate : level.getEntitiesOfClass(Mob.class, area, entity -> entity.isAlive() && !entity.getUUID().equals(mob.getUUID()))) {
            CompoundTag data = candidate.getPersistentData();
            if (!data.hasUUID(TAG_GROUP_ID) || !Objects.equals(candidate.getType(), mob.getType())) {
                continue;
            }
            if (resolveRank(candidate) == MobRank.BOSS) {
                continue;
            }
            double distance = mob.distanceToSqr(candidate);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = candidate;
            }
        }
        return Optional.ofNullable(best);
    }

    private LivingEntity resolveLeader(ServerLevel level, Mob mob, UUID groupId) {
        CompoundTag data = mob.getPersistentData();
        if (data.hasUUID(TAG_LEADER_ID)) {
            Entity entity = level.getEntity(data.getUUID(TAG_LEADER_ID));
            if (entity instanceof LivingEntity living && entity instanceof Mob leaderMob
                && living.isAlive() && Objects.equals(groupId(leaderMob), groupId)) {
                return living;
            }
        }
        List<Mob> members = groupMembers(level, mob, groupId, CommonMobAiSettings.GROUP_RADIUS * 2.0D);
        if (members.isEmpty()) {
            return mob;
        }
        Mob oldest = mob;
        long oldestTick = spawnTick(mob);
        for (Mob member : members) {
            long tick = spawnTick(member);
            if (tick < oldestTick) {
                oldestTick = tick;
                oldest = member;
            }
        }
        data.putUUID(TAG_LEADER_ID, oldest.getUUID());
        return oldest;
    }

    private List<Mob> groupMembers(ServerLevel level, Mob mob, UUID groupId, double radius) {
        List<Mob> members = new ArrayList<>();
        AABB area = mob.getBoundingBox().inflate(radius);
        for (Mob candidate : level.getEntitiesOfClass(Mob.class, area, entity -> entity.isAlive() && !entity.getUUID().equals(mob.getUUID()))) {
            if (Objects.equals(groupId(candidate), groupId)) {
                members.add(candidate);
            }
        }
        return members;
    }

    private UUID groupId(Mob mob) {
        CompoundTag data = mob.getPersistentData();
        return data.hasUUID(TAG_GROUP_ID) ? data.getUUID(TAG_GROUP_ID) : null;
    }

    private long spawnTick(Mob mob) {
        return mob.getPersistentData().getLong(TAG_SPAWN_TICK);
    }

    private void markCombat(Mob mob, long gameTime) {
        mob.getPersistentData().putLong(TAG_LAST_COMBAT_TICK, gameTime);
    }

    private boolean wasInCombatRecently(Mob mob, long gameTime) {
        if (mob.getTarget() != null && mob.getTarget().isAlive()) {
            return true;
        }
        long lastCombatTick = mob.getPersistentData().getLong(TAG_LAST_COMBAT_TICK);
        return gameTime - lastCombatTick <= CommonMobAiSettings.COMBAT_MEMORY_TICKS;
    }

    private void healForEvade(Mob mob) {
        float max = mob.getMaxHealth();
        if (max <= 0.0F) {
            return;
        }
        float heal = (float) Math.max(0.01D, max * CommonMobAiSettings.REGEN_PER_TICK);
        mob.setHealth(Math.min(max, mob.getHealth() + heal));
    }

    private void healForEvade(Mob mob, long gameTime) {
        float max = mob.getMaxHealth();
        if (max <= 0.0F) {
            return;
        }
        long startedAt = mob.getPersistentData().getLong(TAG_EVADE_STARTED_AT);
        int fullHealTicks = Math.max(1, Mth.ceil(1.0D / Math.max(0.0001D, CommonMobAiSettings.REGEN_PER_TICK)));
        if (startedAt > 0L && gameTime - startedAt >= fullHealTicks) {
            mob.setHealth(max);
            return;
        }
        healForEvade(mob);
    }

    private double meleeReach(Mob mob, LivingEntity target) {
        return Math.max(2.6D, 1.5D + mob.getBbWidth() + target.getBbWidth());
    }

    private void ensureFallbackAttackDamage(Mob mob) {
        if (mob.getAttribute(Attributes.ATTACK_DAMAGE) != null) {
            mob.getPersistentData().remove(TAG_FALLBACK_ATTACK_DAMAGE);
            return;
        }
        CompoundTag data = mob.getPersistentData();
        if (data.contains(TAG_FALLBACK_ATTACK_DAMAGE)) {
            return;
        }
        data.putDouble(TAG_FALLBACK_ATTACK_DAMAGE, computeFallbackAttackDamage(mob));
    }

    private double computeFallbackAttackDamage(Mob mob) {
        MobTemplate template = template(mob);
        if (template == null) {
            return 4.0D;
        }
        double attacksPerSecond = Math.max(0.7D, template.attackSpeed());
        double targetTimeToKillPlayer = switch (template.mobType()) {
            case NORMAL -> 30.0D;
            case ELITE -> 24.0D;
            case BOSS -> 18.0D;
        };
        double tunedDamage = (PlayerStatService.estimatedUngearedHealth(template.level()) / targetTimeToKillPlayer) / attacksPerSecond;
        return Math.max(1.0D, Math.max(template.baseDamage() * 0.45D, tunedDamage * template.mobType().damageModifier() * 1.45D));
    }

    private double effectiveAttackDamage(Mob mob) {
        var attackDamageAttribute = mob.getAttribute(Attributes.ATTACK_DAMAGE);
        if (attackDamageAttribute != null) {
            return Math.max(1.0D, attackDamageAttribute.getValue());
        }
        CompoundTag data = mob.getPersistentData();
        if (data.contains(TAG_FALLBACK_ATTACK_DAMAGE)) {
            double cached = data.getDouble(TAG_FALLBACK_ATTACK_DAMAGE);
            if (cached > 0.0D) {
                return cached;
            }
        }
        double computed = computeFallbackAttackDamage(mob);
        data.putDouble(TAG_FALLBACK_ATTACK_DAMAGE, computed);
        return computed;
    }

    private double targetSearchRadius(Mob mob) {
        double follow = mob.getAttribute(Attributes.FOLLOW_RANGE) == null ? 0.0D : mob.getAttributeValue(Attributes.FOLLOW_RANGE);
        return Math.max(12.0D, follow);
    }

    private void markRecent(Mob mob, String key, long gameTime) {
        mob.getPersistentData().putLong(key, gameTime + CommonMobAiSettings.RECENT_WINDOW_TICKS);
    }

    private boolean isRecent(Mob mob, String key, long gameTime) {
        return mob.getPersistentData().getLong(key) >= gameTime;
    }

    private void markAggro(Mob mob, long gameTime) {
        markRecent(mob, TAG_AGGRO_UNTIL, gameTime);
    }

    private boolean shouldRenewAggro(Mob mob, long gameTime) {
        return mob.getPersistentData().getLong(TAG_AGGRO_UNTIL) - gameTime <= CommonMobAiSettings.AGGRO_RENEW_THRESHOLD_TICKS;
    }

    private void clearAggro(Mob mob) {
        mob.getPersistentData().putLong(TAG_AGGRO_UNTIL, 0L);
        mob.getPersistentData().remove(TAG_AGGRO_TARGET_UUID);
        mob.getPersistentData().putLong(TAG_ALLIED_PROVOKED_BY_PLAYER_UNTIL, 0L);
        mob.getPersistentData().putLong(TAG_NEUTRAL_PROVOKED_BY_PLAYER_UNTIL, 0L);
    }

    private void applyPlayerAggro(Mob mob, ServerPlayer player, long gameTime) {
        if (mob == null || player == null || !mob.isAlive() || !isCommonOrElite(mob)) {
            return;
        }
        MobDisposition disposition = resolveDisposition(mob);
        if (disposition == MobDisposition.NEUTRAL) {
            markRecent(mob, TAG_NEUTRAL_PROVOKED_BY_PLAYER_UNTIL, gameTime);
        } else if (disposition == MobDisposition.ALLIED) {
            markRecent(mob, TAG_ALLIED_PROVOKED_BY_PLAYER_UNTIL, gameTime);
        }
        engageTarget(mob, player, gameTime);
    }

    private void touchAggro(Mob mob, LivingEntity target, long gameTime) {
        if (mob == null || target == null || !mob.isAlive() || !target.isAlive() || !isCommonOrElite(mob)) {
            return;
        }
        markCombat(mob, gameTime);
        if (shouldRenewAggro(mob, gameTime)) {
            markAggro(mob, gameTime);
        }
        CompoundTag data = mob.getPersistentData();
        if (!data.hasUUID(TAG_AGGRO_TARGET_UUID) || !data.getUUID(TAG_AGGRO_TARGET_UUID).equals(target.getUUID())) {
            data.putUUID(TAG_AGGRO_TARGET_UUID, target.getUUID());
        }
    }

    private void engageTarget(Mob mob, LivingEntity target, long gameTime) {
        if (mob == null || target == null || !mob.isAlive() || !target.isAlive() || !isCommonOrElite(mob)) {
            return;
        }
        touchAggro(mob, target, gameTime);
        if (!isEvading(mob, gameTime) && canTarget(mob, target, gameTime)) {
            mob.setTarget(target);
        }
    }

    private LivingEntity aggroTarget(Mob mob) {
        if (!(mob.level() instanceof ServerLevel level)) {
            return null;
        }
        CompoundTag data = mob.getPersistentData();
        if (!data.hasUUID(TAG_AGGRO_TARGET_UUID)) {
            return null;
        }
        UUID targetUuid = data.getUUID(TAG_AGGRO_TARGET_UUID);
        Entity entity = level.getEntity(targetUuid);
        if (entity instanceof LivingEntity living && living.isAlive()) {
            return living;
        }
        ServerPlayer player = level.getServer().getPlayerList().getPlayer(targetUuid);
        if (player != null && player.isAlive() && player.level() == level) {
            return player;
        }
        return null;
    }

    private boolean tryRestoreAggroTarget(PathfinderMob mob, long gameTime) {
        LivingEntity target = aggroTarget(mob);
        if (target == null || !canTarget(mob, target, gameTime)) {
            return false;
        }
        engageTarget(mob, target, gameTime);
        return true;
    }

    private boolean neutralAttackedPlayerRecently(Mob neutral, long gameTime) {
        LivingEntity last = neutral.getLastHurtMob();
        return last instanceof ServerPlayer || isRecent(neutral, TAG_NEUTRAL_ATTACKED_PLAYER_UNTIL, gameTime);
    }

    private boolean neutralAttackedHostileRecently(Mob neutral, long gameTime) {
        LivingEntity last = neutral.getLastHurtMob();
        return (last instanceof Mob mob && resolveDisposition(mob) == MobDisposition.HOSTILE)
            || isRecent(neutral, TAG_NEUTRAL_ATTACKED_HOSTILE_UNTIL, gameTime);
    }

    private boolean neutralProvokedByPlayer(Mob neutral, long gameTime) {
        LivingEntity last = neutral.getLastHurtByMob();
        return last instanceof ServerPlayer || isRecent(neutral, TAG_NEUTRAL_PROVOKED_BY_PLAYER_UNTIL, gameTime);
    }

    private boolean alliedProvokedByPlayer(Mob allied, long gameTime) {
        LivingEntity last = allied.getLastHurtByMob();
        return last instanceof ServerPlayer || isRecent(allied, TAG_ALLIED_PROVOKED_BY_PLAYER_UNTIL, gameTime);
    }

    private boolean neutralAttackedByAlliedRecently(Mob neutral, long gameTime) {
        LivingEntity last = neutral.getLastHurtByMob();
        return (last instanceof Mob mob && resolveDisposition(mob) == MobDisposition.ALLIED)
            || isRecent(neutral, TAG_NEUTRAL_ATTACKED_BY_ALLIED_UNTIL, gameTime);
    }

    private boolean neutralAttackedByHostileRecently(Mob neutral, long gameTime) {
        LivingEntity last = neutral.getLastHurtByMob();
        return (last instanceof Mob mob && resolveDisposition(mob) == MobDisposition.HOSTILE)
            || isRecent(neutral, TAG_NEUTRAL_ATTACKED_BY_HOSTILE_UNTIL, gameTime);
    }

    private MobRank resolveRank(Mob mob) {
        CompoundTag data = mob.getPersistentData();
        MobRank stored = MobRank.from(data.getString(TAG_RANK));
        if (stored != null) {
            return stored;
        }
        if (mob instanceof EnderDragon || mob instanceof WitherBoss || hasMarker(mob, TAG_MARK_BOSS)) {
            return MobRank.BOSS;
        }
        MobTemplate template = template(mob);
        if (template != null) {
            return switch (template.mobType()) {
                case NORMAL -> MobRank.COMMON;
                case ELITE -> MobRank.ELITE;
                case BOSS -> MobRank.BOSS;
            };
        }
        if (hasMarker(mob, TAG_MARK_ELITE)) {
            return MobRank.ELITE;
        }
        return MobRank.COMMON;
    }

    private MobDisposition resolveDisposition(LivingEntity entity) {
        if (entity instanceof ServerPlayer) {
            return MobDisposition.ALLIED;
        }
        if (!(entity instanceof Mob mob)) {
            return null;
        }
        return resolveDisposition(mob);
    }

    private MobDisposition resolveDisposition(Mob mob) {
        CompoundTag data = mob.getPersistentData();
        MobDisposition stored = MobDisposition.from(data.getString(TAG_DISPOSITION));
        if (stored != null) {
            return stored;
        }
        if (hasMarker(mob, TAG_MARK_ALLIED)) {
            return MobDisposition.ALLIED;
        }
        if (hasMarker(mob, TAG_MARK_NEUTRAL)) {
            return MobDisposition.NEUTRAL;
        }
        if (hasMarker(mob, TAG_MARK_HOSTILE)) {
            return MobDisposition.HOSTILE;
        }
        MobTemplate template = template(mob);
        if (template != null) {
            return switch (template.hostilityType()) {
                case FRIENDLY -> MobDisposition.ALLIED;
                case NEUTRAL -> MobDisposition.NEUTRAL;
                case HOSTILE -> MobDisposition.HOSTILE;
            };
        }
        if (mob instanceof Monster) {
            return MobDisposition.HOSTILE;
        }
        if (mob instanceof EnderMan || mob instanceof Wolf || mob instanceof Piglin
            || mob instanceof ZombifiedPiglin || mob instanceof IronGolem || mob instanceof Bee) {
            return MobDisposition.NEUTRAL;
        }
        return MobDisposition.NEUTRAL;
    }

    private MobTemplate template(Mob mob) {
        MobSpawnService spawnService = VeyloriaServerRuntime.instance().mobSpawnService();
        return spawnService == null ? null : spawnService.template(mob.getUUID());
    }

    private boolean hasMarker(Mob mob, String key) {
        CompoundTag data = mob.getPersistentData();
        if (data.getBoolean(key)) {
            return true;
        }
        String value = data.getString(key);
        if (!value.isBlank() && ("true".equalsIgnoreCase(value) || "1".equals(value))) {
            return true;
        }
        return mob.getTags().contains(key);
    }

    private boolean isAuthenticated(ServerPlayer player) {
        AuthService authService = VeyloriaServerRuntime.instance().authService();
        return authService == null || authService.sessionManager().isAuthenticated(player.getUUID());
    }

    private boolean canAssistFrom(PathfinderMob source, Mob ally) {
        MobDisposition sourceDisposition = resolveDisposition(source);
        MobDisposition allyDisposition = resolveDisposition(ally);
        if (sourceDisposition == null || allyDisposition == null) {
            return false;
        }
        if (sourceDisposition == allyDisposition) {
            return true;
        }
        UUID sourceGroup = groupId(source);
        UUID allyGroup = groupId(ally);
        return sourceGroup != null && sourceGroup.equals(allyGroup);
    }

    private boolean isIdleMovementState(PathfinderMob mob, long gameTime) {
        return mob.getTarget() == null
            && !isEvading(mob, gameTime)
            && !isAggroed(mob, gameTime)
            && !wasInCombatRecently(mob, gameTime);
    }

    public record DamageDecision(boolean allowed, String errorMessage) {
        public static DamageDecision allow() {
            return new DamageDecision(true, null);
        }

        public static DamageDecision deny(String errorMessage) {
            return new DamageDecision(false, errorMessage);
        }
    }

    public enum MobRank {
        COMMON("common"),
        ELITE("elite"),
        BOSS("boss");

        private final String id;

        MobRank(String id) {
            this.id = id;
        }

        public String id() {
            return id;
        }

        public static MobRank from(String id) {
            if (id == null || id.isBlank()) {
                return null;
            }
            for (MobRank rank : values()) {
                if (rank.id.equalsIgnoreCase(id)) {
                    return rank;
                }
            }
            return null;
        }
    }

    public enum MobDisposition {
        ALLIED("allied"),
        NEUTRAL("neutral"),
        HOSTILE("hostile");

        private final String id;

        MobDisposition(String id) {
            this.id = id;
        }

        public String id() {
            return id;
        }

        public static MobDisposition from(String id) {
            if (id == null || id.isBlank()) {
                return null;
            }
            for (MobDisposition disposition : values()) {
                if (disposition.id.equalsIgnoreCase(id)) {
                    return disposition;
                }
            }
            return null;
        }
    }

    private interface PatchedGoal {
    }

    private static final class EvadeReturnHomeGoal extends Goal implements PatchedGoal {
        private final CommonMobAiService service;
        private final PathfinderMob mob;

        private EvadeReturnHomeGoal(CommonMobAiService service, PathfinderMob mob) {
            this.service = service;
            this.mob = mob;
            setFlags(EnumSet.of(Flag.MOVE, Flag.TARGET, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            return service.shouldStartEvade(mob, mob.level().getGameTime());
        }

        @Override
        public boolean canContinueToUse() {
            long gameTime = mob.level().getGameTime();
            return service.isEvading(mob, gameTime) && !service.shouldFinishEvade(mob);
        }

        @Override
        public void start() {
            service.startEvade(mob, mob.level().getGameTime());
        }

        @Override
        public void tick() {
            long gameTime = mob.level().getGameTime();
            service.tickEvade(mob, gameTime);
            if (service.shouldFinishEvade(mob)) {
                service.stopEvade(mob, gameTime);
            }
        }

        @Override
        public void stop() {
            service.stopEvade(mob, mob.level().getGameTime());
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }
    }

    private static final class CannotReachTrackerGoal extends Goal implements PatchedGoal {
        private final CommonMobAiService service;
        private final PathfinderMob mob;

        private CannotReachTrackerGoal(CommonMobAiService service, PathfinderMob mob) {
            this.service = service;
            this.mob = mob;
        }

        @Override
        public boolean canUse() {
            return true;
        }

        @Override
        public boolean canContinueToUse() {
            return true;
        }

        @Override
        public void tick() {
            long gameTime = mob.level().getGameTime();
            if (service.isEvading(mob, gameTime)) {
                mob.getPersistentData().putInt(TAG_CANNOT_REACH, 0);
                return;
            }
            LivingEntity target = mob.getTarget();
            if ((target == null || !target.isAlive()) && service.isAggroed(mob, gameTime)) {
                if (service.tryRestoreAggroTarget(mob, gameTime)) {
                    return;
                }
            }
            if (target == null || !target.isAlive()) {
                mob.getPersistentData().putInt(TAG_CANNOT_REACH, 0);
                return;
            }
            if (!service.canTarget(mob, target, gameTime)) {
                mob.setTarget(null);
                mob.getPersistentData().putInt(TAG_CANNOT_REACH, 0);
                return;
            }
            service.markCombat(mob, gameTime);
            if (service.isReachableForAttack(mob, target)) {
                mob.getPersistentData().putInt(TAG_CANNOT_REACH, 0);
                return;
            }
            mob.getPersistentData().putInt(TAG_CANNOT_REACH, mob.getPersistentData().getInt(TAG_CANNOT_REACH) + 1);
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }
    }

    private static final class GroupCohesionGoal extends Goal implements PatchedGoal {
        private final CommonMobAiService service;
        private final PathfinderMob mob;
        private final double speed;
        private Vec3 target;
        private long nextRepathTick;

        private GroupCohesionGoal(CommonMobAiService service, PathfinderMob mob, double speed) {
            this.service = service;
            this.mob = mob;
            this.speed = speed;
            setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            long gameTime = mob.level().getGameTime();
            if (!service.isIdleMovementState(mob, gameTime)) {
                return false;
            }
            target = service.cohesionTarget(mob);
            double startDistance = CommonMobAiSettings.GROUP_RADIUS * 1.8D;
            return target != null && mob.distanceToSqr(target) > startDistance * startDistance;
        }

        @Override
        public boolean canContinueToUse() {
            long gameTime = mob.level().getGameTime();
            if (!service.isIdleMovementState(mob, gameTime)) {
                return false;
            }
            double keepDistance = CommonMobAiSettings.GROUP_RADIUS * 1.2D;
            return target != null && mob.distanceToSqr(target) > keepDistance * keepDistance;
        }

        @Override
        public void start() {
            nextRepathTick = 0L;
            if (target != null) {
                mob.getNavigation().moveTo(target.x, target.y, target.z, speed);
            }
        }

        @Override
        public void tick() {
            long gameTime = mob.level().getGameTime();
            if (gameTime < nextRepathTick) {
                return;
            }
            target = service.cohesionTarget(mob);
            if (target != null) {
                mob.getNavigation().moveTo(target.x, target.y, target.z, speed);
            }
            nextRepathTick = gameTime + 20L;
        }

        @Override
        public void stop() {
            target = null;
            mob.getNavigation().stop();
        }
    }

    private static final class WanderWithinHomeGoal extends Goal implements PatchedGoal {
        private final CommonMobAiService service;
        private final PathfinderMob mob;
        private final double speed;
        private Vec3 target;

        private WanderWithinHomeGoal(CommonMobAiService service, PathfinderMob mob, double speed) {
            this.service = service;
            this.mob = mob;
            this.speed = speed;
            setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            long gameTime = mob.level().getGameTime();
            if (!service.isIdleMovementState(mob, gameTime)) {
                return false;
            }
            if (mob.getRandom().nextInt(12) != 0) {
                return false;
            }
            target = service.randomHomePoint(mob);
            return target != null;
        }

        @Override
        public boolean canContinueToUse() {
            long gameTime = mob.level().getGameTime();
            if (!service.isIdleMovementState(mob, gameTime)) {
                return false;
            }
            return target != null && !mob.getNavigation().isDone();
        }

        @Override
        public void start() {
            if (target != null) {
                mob.getNavigation().moveTo(target.x, target.y, target.z, speed);
            }
        }

        @Override
        public void stop() {
            target = null;
        }
    }

    private static final class AggroRecoverGoal extends Goal implements PatchedGoal {
        private final CommonMobAiService service;
        private final PathfinderMob mob;

        private AggroRecoverGoal(CommonMobAiService service, PathfinderMob mob) {
            this.service = service;
            this.mob = mob;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            long gameTime = mob.level().getGameTime();
            return !service.isEvading(mob, gameTime)
                && service.isAggroed(mob, gameTime)
                && !service.tryRestoreAggroTarget(mob, gameTime)
                && (mob.getTarget() == null || !mob.getTarget().isAlive());
        }

        @Override
        public boolean canContinueToUse() {
            long gameTime = mob.level().getGameTime();
            return !service.isEvading(mob, gameTime)
                && service.isAggroed(mob, gameTime)
                && (mob.getTarget() == null || !mob.getTarget().isAlive())
                && mob.getHealth() < mob.getMaxHealth();
        }

        @Override
        public void start() {
            mob.setTarget(null);
            mob.getNavigation().stop();
        }

        @Override
        public void tick() {
            mob.setTarget(null);
            mob.getNavigation().stop();
            service.healForEvade(mob);
            if (mob.getHealth() >= mob.getMaxHealth()) {
                service.clearAggro(mob);
            }
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }
    }

    private static final class CommonMeleeAttackGoal extends Goal implements PatchedGoal {
        private final CommonMobAiService service;
        private final PathfinderMob mob;
        private final double speed;
        private int cooldownTicks;

        private CommonMeleeAttackGoal(CommonMobAiService service, PathfinderMob mob, double speed) {
            this.service = service;
            this.mob = mob;
            this.speed = speed;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            LivingEntity target = mob.getTarget();
            long gameTime = mob.level().getGameTime();
            return target != null && target.isAlive() && !service.isEvading(mob, gameTime) && service.canTarget(mob, target, gameTime);
        }

        @Override
        public boolean canContinueToUse() {
            return canUse();
        }

        @Override
        public void start() {
            cooldownTicks = 0;
        }

        @Override
        public void tick() {
            LivingEntity target = mob.getTarget();
            if (target == null) {
                return;
            }
            service.touchAggro(mob, target, mob.level().getGameTime());
            mob.getLookControl().setLookAt(target, 30.0F, 30.0F);
            mob.getNavigation().moveTo(target, speed);
            if (cooldownTicks > 0) {
                cooldownTicks--;
            }
            if (!mob.hasLineOfSight(target) || mob.distanceTo(target) > service.meleeReach(mob, target) || cooldownTicks > 0) {
                return;
            }
            mob.swing(mob.getUsedItemHand());
            float damage = (float) service.effectiveAttackDamage(mob);
            if (target.hurt(mob.damageSources().mobAttack(mob), damage)) {
                service.recordSuccessfulDamage(mob, target, mob.level().getGameTime());
            }
            if (target.isAlive()) {
                mob.setTarget(target);
            }
            var attackSpeedAttribute = mob.getAttribute(Attributes.ATTACK_SPEED);
            double attackSpeed = attackSpeedAttribute == null ? 1.0D : Math.max(0.25D, attackSpeedAttribute.getValue());
            cooldownTicks = Math.max(8, Mth.ceil(20.0D / attackSpeed));
        }
    }

    private static final class HurtByCommonGoal extends TargetGoal implements PatchedGoal {
        private final CommonMobAiService service;
        private final PathfinderMob mob;
        private LivingEntity target;
        private int lastTimestamp;

        private HurtByCommonGoal(CommonMobAiService service, PathfinderMob mob) {
            super(mob, false);
            this.service = service;
            this.mob = mob;
        }

        @Override
        public boolean canUse() {
            LivingEntity aggressor = mob.getLastHurtByMob();
            int timestamp = mob.getLastHurtByMobTimestamp();
            if (aggressor == null || !aggressor.isAlive() || timestamp == lastTimestamp) {
                return false;
            }
            long gameTime = mob.level().getGameTime();
            if (service.isEvading(mob, gameTime) || !service.canTarget(mob, aggressor, gameTime)) {
                return false;
            }
            target = aggressor;
            return true;
        }

        @Override
        public void start() {
            if (target != null) {
                service.engageTarget(mob, target, mob.level().getGameTime());
            }
            lastTimestamp = mob.getLastHurtByMobTimestamp();
            super.start();
        }

        @Override
        public boolean canContinueToUse() {
            return false;
        }
    }

    private static final class AssistAllyGoal extends TargetGoal implements PatchedGoal {
        private final CommonMobAiService service;
        private final PathfinderMob mob;
        private LivingEntity target;
        private int cooldown;

        private AssistAllyGoal(CommonMobAiService service, PathfinderMob mob) {
            super(mob, false);
            this.service = service;
            this.mob = mob;
        }

        @Override
        public boolean canUse() {
            if (cooldown > 0) {
                cooldown--;
                return false;
            }
            long gameTime = mob.level().getGameTime();
            if (service.isAggroed(mob, gameTime) && (mob.getTarget() == null || !mob.getTarget().isAlive())) {
                service.tryRestoreAggroTarget(mob, gameTime);
                return false;
            }
            cooldown = CommonMobAiSettings.TARGET_SCAN_INTERVAL_TICKS;
            target = service.findAssistTarget(mob, gameTime);
            return target != null;
        }

        @Override
        public void start() {
            if (target != null) {
                service.engageTarget(mob, target, mob.level().getGameTime());
            }
            super.start();
        }

        @Override
        public boolean canContinueToUse() {
            return false;
        }
    }

    private static final class AcquireTargetGoal extends TargetGoal implements PatchedGoal {
        private final CommonMobAiService service;
        private final PathfinderMob mob;
        private LivingEntity target;
        private int cooldown;

        private AcquireTargetGoal(CommonMobAiService service, PathfinderMob mob) {
            super(mob, false);
            this.service = service;
            this.mob = mob;
        }

        @Override
        public boolean canUse() {
            if (cooldown > 0) {
                cooldown--;
                return false;
            }
            cooldown = CommonMobAiSettings.TARGET_SCAN_INTERVAL_TICKS;
            long gameTime = mob.level().getGameTime();
            if (service.isAggroed(mob, gameTime) && (mob.getTarget() == null || !mob.getTarget().isAlive())) {
                service.tryRestoreAggroTarget(mob, gameTime);
                return false;
            }
            if (service.isEvading(mob, gameTime)) {
                return false;
            }
            LivingEntity current = mob.getTarget();
            if (current != null && current.isAlive() && service.canTarget(mob, current, gameTime)) {
                return false;
            }
            target = service.findAcquireTarget(mob, gameTime);
            return target != null;
        }

        @Override
        public void start() {
            if (target != null) {
                service.engageTarget(mob, target, mob.level().getGameTime());
            }
            super.start();
        }

        @Override
        public boolean canContinueToUse() {
            return false;
        }
    }
}
