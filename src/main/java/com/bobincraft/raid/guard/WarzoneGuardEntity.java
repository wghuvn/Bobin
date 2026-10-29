package com.bobincraft.raid.guard;

import com.bobincraft.raid.WarzoneEncounterManager;
import com.bobincraft.warzone.WarzoneBasePlan;
import com.bobincraft.warzone.Faction;
import com.bobincraft.warzone.WarzoneDimension;
import com.bobincraft.warzone.CombatZonePlan;
import com.bobincraft.warzone.ResidentialPlan;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.ai.goal.SwimGoal;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.mob.PillagerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

/**
 * A registered entity type is essential here: vanilla pillagers would reload their vanilla goals.
 * Perception, faction identity, finite firearm state, patrols and territory are server owned.
 */
public final class WarzoneGuardEntity extends PillagerEntity implements com.bobincraft.combat.InjuryCarrier {
    private static final TrackedData<Integer> MISSING_LIMBS = DataTracker.registerData(WarzoneGuardEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final java.util.UUID LEG_INJURY_SPEED = java.util.UUID.fromString("bb6b1540-ae9d-4a74-9dd6-518905d0fd28");
    private static final TrackedData<Boolean> LEG_TRAUMA=DataTracker.registerData(WarzoneGuardEntity.class,TrackedDataHandlerRegistry.BOOLEAN);
    private int legTraumaTicks,retreatTicks;
    private final com.bobincraft.combat.InjuryState injuries = new com.bobincraft.combat.InjuryState();
    private float previousProneAmount, proneAmount;
    private static final int MAX_TERRITORY_RADIUS = 96;
    private static final Faction[] FACTIONS = Faction.values();
    private static final TrackedData<Integer> FACTION = DataTracker.registerData(WarzoneGuardEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Integer> VARIANT = DataTracker.registerData(WarzoneGuardEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Boolean> HUB_GUARD = DataTracker.registerData(WarzoneGuardEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
    private NpcWeapon weapon = NpcWeapon.STANDARD;
    private FactionLoadouts.Profile loadout;
    private NpcFireControl firearm;
    private int danger;

    private final GuardBrain brain = new GuardBrain();
    private BlockPos post = BlockPos.ORIGIN;
    private BlockPos territory = BlockPos.ORIGIN;
    private final List<BlockPos> patrol = new ArrayList<>();
    private WarzoneBasePlan.SpotRole role = WarzoneBasePlan.SpotRole.SOLDIER;
    private Vec3d lastKnownPosition;
    private int siteId;
    private int rosterSlot = -1;
    private boolean residentialResident;
    private int territoryRadius = 48;
    private boolean squareTerritory;
    private int patrolIndex;
    private BlockPos workPost = BlockPos.ORIGIN;
    private BlockPos depot = BlockPos.ORIGIN;
    private final GuardDepotSupply depotSupply = new GuardDepotSupply();
    private final GuardDutySchedule duties = new GuardDutySchedule();
    private int searchIndex;
    private int blockedPaths;
    private int nextWorkAnimation;
    private int nextRepositionAge;
    private int nextPathAge;
    private int nextPerceptionAge;
    private int nextAlertAge;
    private boolean configured;
    private boolean manuallySpawned;
    private boolean simulationActive;
    private com.bobincraft.drone.DroneEntity droneThreat;
    private DroneThreatPolicy.Response droneResponse=DroneThreatPolicy.Response.IGNORE;
    private Vec3d survivalThreat;
    private int droneMemoryUntil;
    private int proneStartedAge = -100;
    private boolean killedByBlast;


    public WarzoneGuardEntity(EntityType<? extends PillagerEntity> type, World world) {
        super(type, world);
        setPersistent();
        setAbleToJoinRaid(false);
    }

    @Override
    protected void initDataTracker() {
        super.initDataTracker();
        dataTracker.startTracking(FACTION, Faction.REGULAR_ARMY.ordinal());
        dataTracker.startTracking(VARIANT, 0);
        dataTracker.startTracking(HUB_GUARD, false);
        dataTracker.startTracking(MISSING_LIMBS, 0);
        dataTracker.startTracking(LEG_TRAUMA,false);
    }

    @Override public com.bobincraft.combat.InjuryState bobinInjuries() { return injuries; }
    public int missingLimbs() { return dataTracker.get(MISSING_LIMBS); }
    public boolean isInjuryProne() { return injuries != null && (com.bobincraft.combat.GuardInjuryPose.prone(missingLimbs())||dataTracker.get(LEG_TRAUMA)); }
    public boolean canUseHands() { return injuries == null || com.bobincraft.combat.GuardInjuryPose.canUseHands(missingLimbs()); }
    public float injuryProneAmount(float delta) {
        return !isAlive() && isInjuryProne() ? 1 : net.minecraft.util.math.MathHelper.lerp(delta, previousProneAmount, proneAmount);
    }

    @Override public net.minecraft.entity.EntityDimensions getDimensions(net.minecraft.entity.EntityPose pose) {
        return isInjuryProne() ? net.minecraft.entity.EntityDimensions.changing(
                com.bobincraft.combat.GuardInjuryPose.WIDTH, com.bobincraft.combat.GuardInjuryPose.HEIGHT) : super.getDimensions(pose);
    }
    @Override protected float getActiveEyeHeight(net.minecraft.entity.EntityPose pose, net.minecraft.entity.EntityDimensions size) {
        return isInjuryProne() ? 0.55f : super.getActiveEyeHeight(pose, size);
    }
    @Override public void onTrackedDataSet(TrackedData<?> data) {
        super.onTrackedDataSet(data);
        if (MISSING_LIMBS.equals(data)||LEG_TRAUMA.equals(data)) { calculateDimensions(); updateInjuryBounds(); }
    }
    private void updateInjuryBounds() {
        if (isInjuryProne()) setBoundingBox(com.bobincraft.combat.GuardInjuryPose.bounds(getPos(), getBodyYaw()));
    }
    @Override protected void jump() { if (!isInjuryProne() || isTouchingWater()) super.jump(); }
    @Override public boolean canGather(ItemStack stack) { return canUseHands() && super.canGather(stack); }
    @Override public boolean canEquip(ItemStack stack) { return canUseHands() && super.canEquip(stack); }

    /** Injured corpses remain briefly visible; native removal/XP still happens exactly once. */
    @Override protected void updatePostDeath() {
        if (!isHubGuard() && deathTime < com.bobincraft.combat.GuardInjuryPose.CORPSE_TICKS - 1) { deathTime++; return; }
        if (!isHubGuard()) deathTime = 19;
        super.updatePostDeath();
    }

    private void enforceInjuredHands(boolean drop) {
        if (canUseHands()) return;
        setCanPickUpLoot(false);
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND}) {
            ItemStack held = getEquippedStack(slot);
            if (held.isEmpty()) continue;
            if (drop && isAlive() && slot != EquipmentSlot.MAINHAND) dropStack(held.copy());
            equipStack(slot, ItemStack.EMPTY);
        }
        setAttacking(false);
        stopUsingItem();
    }

    /** Limb loss affects actual navigation/firearm state as well as the synchronized model. */
    public void refreshInjuries(boolean dropWeapon) {
        boolean wasProne = isInjuryProne();
        int dPose = isAlive() ? 0 : com.bobincraft.combat.GuardInjuryPose.deathPose(missingLimbs());
        dataTracker.set(MISSING_LIMBS, com.bobincraft.combat.GuardInjuryPose.pack(injuries.missingLimbs(), dPose));
        if (!wasProne && isInjuryProne() && isAlive()) proneStartedAge = age;
        var speed = getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);
        if (speed != null) {
            speed.removeModifier(LEG_INJURY_SPEED);
            double penalty = com.bobincraft.combat.GuardMobility.speedPenalty(missingLimbs(),dataTracker.get(LEG_TRAUMA));
            if (penalty != 0) speed.addTemporaryModifier(new net.minecraft.entity.attribute.EntityAttributeModifier(
                    LEG_INJURY_SPEED, "Bobin missing leg", penalty,
                    net.minecraft.entity.attribute.EntityAttributeModifier.Operation.MULTIPLY_TOTAL));
        }
        enforceInjuredHands(dropWeapon);
    }

    public void noteRegionalWound(com.bobincraft.combat.HitRegion region,float damage){
        if(getWorld().isClient||!isAlive()||isHubGuard())return;
        if(com.bobincraft.combat.GuardMobility.legWound(region,damage)){
            legTraumaTicks=com.bobincraft.combat.GuardMobility.RECOVERY_TICKS;dataTracker.set(LEG_TRAUMA,true);refreshInjuries(false);
        }
    }

    public Faction faction() { return FACTIONS[Math.floorMod(dataTracker.get(FACTION), FACTIONS.length)]; }
    public int appearanceVariant() { return dataTracker.get(VARIANT); }
    public boolean isHubGuard() { return dataTracker.get(HUB_GUARD); }
    public boolean isResidentialResident() { return residentialResident; }
    public NpcWeapon firearmType() { return weapon; }

    /** Friendly, lightweight persistent soldier. Hub construction owns placement and uniqueness. */
    public void assignHubGuard(BlockPos assignedPost) {
        dataTracker.set(HUB_GUARD, true);
        // Dialogue explicitly plays the original sentry voice; suppress inherited pillager grunts.
        setSilent(true);
        dataTracker.set(FACTION, Faction.REGULAR_ARMY.ordinal());
        dataTracker.set(VARIANT, 0);
        post = assignedPost.toImmutable();
        territory = post;
        siteId = -1;
        rosterSlot = -1;
        residentialResident = false;
        configured = true;
        weapon = NpcWeapon.STANDARD;
        equipStack(EquipmentSlot.MAINHAND, new ItemStack(weapon.item()));
        setEquipmentDropChance(EquipmentSlot.MAINHAND, 0);
        setAiDisabled(true);
        setInvulnerable(true);
        setTarget(null);
        setAttacking(false);
        setAbleToJoinRaid(false);
        setPersistent();
    }

    @Override
    protected boolean isDisallowedInPeaceful() { return !isHubGuard() && !manuallySpawned && rosterSlot < 0; }

    @Override public net.minecraft.entity.EntityData initialize(net.minecraft.world.ServerWorldAccess world,
            net.minecraft.world.LocalDifficulty difficulty, net.minecraft.entity.SpawnReason reason,
            net.minecraft.entity.EntityData data, NbtCompound nbt) {
        var result = super.initialize(world, difficulty, reason, data, nbt);
        if (!configured && (reason == net.minecraft.entity.SpawnReason.SPAWN_EGG || reason == net.minecraft.entity.SpawnReason.DISPENSER)) {
            manuallySpawned = true;
            // A manually spawned guard has a real weapon/AI but never occupies a generated site's roster slot.
            siteId = rosterSlot = -1;
            post = territory = workPost = depot = getBlockPos().toImmutable();
            territoryRadius = 48;
            role = WarzoneBasePlan.SpotRole.SENTRY;
            configured = true;
            duties.assign(GuardDutySchedule.Assignment.SENTRY, getUuid().hashCode());
            configureArmament(true);
        }
        return result;
    }

    @Override
    protected void initGoals() {
        goalSelector.add(0, new SwimGoal(this));
        goalSelector.add(1, new SurvivalGoal());
        goalSelector.add(2, new VehicleOperatorGoal());
        goalSelector.add(3, new FirearmApproachGoal());
        goalSelector.add(4, new AssignedPostGoal());
        // Intentionally no vanilla targetSelector, raid movement, or random wander goals.
    }

    public void assign(WarzoneBasePlan.Site site, WarzoneBasePlan.TacticalSpot spot, List<BlockPos> route) {
        residentialResident = false;
        siteId = site.id;
        post = getBlockPos().toImmutable();
        territory = new BlockPos(site.cx, site.baseY, site.cz);
        territoryRadius = Math.max(16, Math.min(MAX_TERRITORY_RADIUS, site.flatRadius + 22));
        squareTerritory = false;
        role = spot.role();
        workPost = post;
        duties.assign(role == WarzoneBasePlan.SpotRole.SENTRY ? GuardDutySchedule.Assignment.SENTRY
                : GuardDutySchedule.Assignment.PATROL, site.id * 193 + spot.x() * 31 + spot.z());
        patrol.clear();
        if (role != WarzoneBasePlan.SpotRole.SENTRY) {
            route.stream().filter(this::insideTerritory).limit(16).forEach(patrol::add);
        }
        configured = true;
        dataTracker.set(FACTION, site.faction.ordinal());
        dataTracker.set(VARIANT, Math.floorMod(31 * site.id + spot.x() * 17 + spot.z() * 13, 3));
        danger = FactionLoadouts.danger(site);
        configureArmament(true);
    }

    /** New compounds use explicit work stations and courtyard patrol routes from their actual blueprint. */
    public void assignCombatSite(CombatZonePlan.Site site, CombatZonePlan.Post assignment) {
        residentialResident = false;
        siteId = site.id();
        rosterSlot = assignment.index();
        post = point(assignment.spawn());
        workPost = point(assignment.focus());
        depot = site.posts().stream().filter(p->p.duty()==CombatZonePlan.Duty.QUARTERMASTER)
                .map(p->point(p.spawn())).findFirst().orElse(post);
        territory = new BlockPos(site.x(), site.groundY() + 1, site.z());
        territoryRadius = Math.max(16, Math.min(MAX_TERRITORY_RADIUS, site.radius() + 12));
        squareTerritory = true;
        role = assignment.duty() == CombatZonePlan.Duty.SENTRY ? WarzoneBasePlan.SpotRole.SENTRY
                : WarzoneBasePlan.SpotRole.SOLDIER;
        duties.assign(GuardDutySchedule.Assignment.valueOf(assignment.duty().name()), site.id() * 97 + assignment.index() * 317);
        patrol.clear();
        assignment.route().stream().map(WarzoneGuardEntity::point).filter(this::insideTerritory)
                .limit(16).forEach(patrol::add);
        configured = true;
        dataTracker.set(FACTION, (site.faction() == CombatZonePlan.Faction.MILITARY ? Faction.REGULAR_ARMY : Faction.ASH_RAIDERS).ordinal());
        dataTracker.set(VARIANT, Math.floorMod(site.id() * 31 + assignment.index(), 3));
        danger = site.radius() >= 42 ? 2 : 1;
        configureArmament(true);
    }

    /** Forest occupants use authored lanes, yards and work points, never military courtyard routes. */
    public void assignResidentialSite(ResidentialPlan.Site site, ResidentialPlan.Post assignment) {
        residentialResident = true;
        siteId = site.id();
        rosterSlot = assignment.index();
        post = point(assignment.spawn());
        workPost = point(assignment.focus());
        depot = site.posts().stream().filter(p -> p.duty() == ResidentialPlan.Duty.QUARTERMASTER)
                .map(p -> point(p.spawn())).findFirst().orElse(post);
        territory = new BlockPos(site.x(), site.groundY() + 1, site.z());
        territoryRadius = Math.max(16, Math.min(MAX_TERRITORY_RADIUS, site.radius() + 12));
        squareTerritory = true;
        role = assignment.duty() == ResidentialPlan.Duty.SENTRY ? WarzoneBasePlan.SpotRole.SENTRY
                : WarzoneBasePlan.SpotRole.SOLDIER;
        duties.assign(ResidentialResidents.assignment(assignment.duty()), site.id() * 97 + assignment.index() * 317);
        patrol.clear();
        assignment.route().stream().map(WarzoneGuardEntity::point).filter(this::insideTerritory)
                .limit(16).forEach(patrol::add);
        configured = true;
        dataTracker.set(FACTION, ResidentialResidents.faction(site.group()).ordinal());
        dataTracker.set(VARIANT, Math.floorMod(site.id() * 31 + assignment.index(), 3));
        danger = site.kind() == ResidentialPlan.Kind.DERELICT ? 1 : 0;
        configureArmament(true);
    }

    private static BlockPos point(CombatZonePlan.Point p) { return new BlockPos(p.x(), p.y(), p.z()); }
    private static BlockPos point(ResidentialPlan.Point p) { return new BlockPos(p.x(), p.y(), p.z()); }

    private void configureArmament(boolean chooseWeapon) {
        loadout = FactionLoadouts.forFaction(faction());
        if (chooseWeapon) weapon = FactionLoadouts.choose(loadout, role, danger, appearanceVariant());
        firearm = new NpcFireControl(loadout.fireSettings(weapon, danger));
        if (canUseHands() && (chooseWeapon || !getMainHandStack().isOf(weapon.item()))) {
            equipStack(EquipmentSlot.MAINHAND, new ItemStack(weapon.item()));
        }
        if (chooseWeapon) {
            equipStack(EquipmentSlot.HEAD, new ItemStack(loadout.armored() ? Items.IRON_HELMET : Items.LEATHER_HELMET));
            equipStack(EquipmentSlot.CHEST, new ItemStack(loadout.armored() && danger >= 2 ? Items.IRON_CHESTPLATE : Items.LEATHER_CHESTPLATE));
            for (EquipmentSlot slot : EquipmentSlot.values()) setEquipmentDropChance(slot, 0.0F);
            setEquipmentDropChance(EquipmentSlot.MAINHAND, 0.0F);
            var health = getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH);
            if (health != null) health.setBaseValue(faction().isBandit() ? 20 : danger >= 2 ? 28 : 24);
            setHealth(getMaxHealth());
        }
    }

    public int siteId() { return siteId; }
    public int rosterSlot() { return rosterSlot; }
    public GuardBrain.State guardState() { return brain.state(); }
    public GuardDutySchedule.Activity dutyActivity() { return duties.activity(); }

    public boolean isHostileTarget(LivingEntity target) {
        if (isHubGuard() || !target.isAlive() || getWorld().getDifficulty() == net.minecraft.world.Difficulty.PEACEFUL) return false;
        if (target instanceof ServerPlayerEntity player) return validTarget(player);
        return target instanceof WarzoneGuardEntity guard && !guard.isHubGuard()
                && GuardFactionRelations.hostile(faction(), guard.faction());
    }

    public void hearGunfire(Vec3d position) {
        if (!configured || isHubGuard() || !insideTerritory(BlockPos.ofFloored(position))) return;
        lastKnownPosition = position;
        brain.hearNoise();
        nextPerceptionAge = 0;
    }

    public void hearAllyAlert(Vec3d position) {
        hearGunfire(position);
    }

    @Override
    public void tickMovement() {
        if(!isAlive()){
            getNavigation().stop();setAttacking(false);limbAnimator.setSpeed(0);
            setVelocity(0,isOnGround()?0:Math.min(0,getVelocity().y),0);
        }
        updateInjuryBounds();
        super.tickMovement();
        updateInjuryBounds();
        previousProneAmount = proneAmount;
        proneAmount = com.bobincraft.combat.GuardInjuryPose.advanceFall(proneAmount, isInjuryProne());
        com.bobincraft.combat.InjurySystem.tick(this);
        if (!isAlive()) return;
        if (!getWorld().isClient){
            enforceInjuredHands(true);if(retreatTicks>0)retreatTicks--;
            if(legTraumaTicks>0&&--legTraumaTicks==0){if(getWorld().isSpaceEmpty(this,net.minecraft.entity.EntityDimensions.changing(.6f,1.95f).getBoxAt(getPos()))){dataTracker.set(LEG_TRAUMA,false);refreshInjuries(false);}else legTraumaTicks=20;}
        }
        if (getWorld().isClient || !configured || isHubGuard()) return;
        duties.tick(simulationActive && isCalm());
        tickFirearm();
        if (age < nextPerceptionAge) return;
        // UUID-derived stagger prevents a freshly spawned squad raycasting on the same tick.
        int phase = Math.floorMod(getUuid().hashCode(), 10);
        if ((age + phase) % 10 != 0) return;
        ServerWorld world = (ServerWorld) getWorld();
        ServerPlayerEntity nearest = null;
        double nearestDistance = 112.0 * 112.0;
        for (ServerPlayerEntity player : world.getPlayers()) {
            // Creative observers still see residents working, but are never attacked.
            if (!player.isAlive() || player.isSpectator()) continue;
            double distance = squaredDistanceTo(player);
            if (distance < nearestDistance) {
                nearestDistance = distance;
                nearest = player;
            }
        }
        simulationActive = nearest != null;
        int perceptionInterval = nearestDistance > 64.0 * 64.0 ? 30 : 10;
        nextPerceptionAge = age + perceptionInterval;
        if (!simulationActive) {
            setTarget(null);
            getNavigation().stop();
            return;
        }

        perceiveDrones(world);
        LivingEntity visible = null;
        LivingEntity oldTarget = getTarget();
        if (oldTarget != null && canPerceive(oldTarget, true)) visible = oldTarget;
        if (visible == null) {
            double closest = 48.0 * 48.0;
            for (ServerPlayerEntity player : world.getPlayers()) {
                double distance = squaredDistanceTo(player);
                boolean alerted = brain.state() == GuardBrain.State.SEARCHING || brain.state() == GuardBrain.State.ALERTED
                        || brain.state() == GuardBrain.State.IN_COMBAT;
                if (distance < closest && canPerceive(player, alerted)) {
                    visible = player;
                    closest = distance;
                }
            }
            // A bounded local scan, never an entity-wide search or a through-wall faction radar.
            for (WarzoneGuardEntity other : world.getEntitiesByClass(WarzoneGuardEntity.class,
                    getBoundingBox().expand(48), guard -> guard != this && isHostileTarget(guard))) {
                double distance = squaredDistanceTo(other);
                boolean tracking = brain.state() == GuardBrain.State.IN_COMBAT || brain.state() == GuardBrain.State.SEARCHING;
                if (distance < closest && canPerceive(other, tracking)) { visible = other; closest = distance; }
            }
        }

        GuardBrain.State previous = brain.state();
        brain.update(visible != null, squaredDistanceTo(Vec3d.ofCenter(post)) < 9.0, !patrol.isEmpty(), perceptionInterval);
        if (visible != null) {
            lastKnownPosition = visible.getPos();
            searchIndex = 0;
            getLookControl().lookAt(visible, 30.0F, 30.0F);
            boolean confirmed = brain.state() == GuardBrain.State.ALERTED || brain.state() == GuardBrain.State.IN_COMBAT;
            setTarget(confirmed ? visible : null);
            if (confirmed && previous != GuardBrain.State.IN_COMBAT && age >= nextAlertAge) {
                nextAlertAge = age + 160;
                WarzoneEncounterManager.alertAllies(this, visible.getPos());
            }
        } else {
            // Lost sight starts investigation rather than shooting through a wall.
            setTarget(null);
        }
    }

    private boolean validTarget(ServerPlayerEntity player) {
        return !isHubGuard() && player.isAlive() && !player.isSpectator() && !player.isCreative();
    }

    private boolean isCalm() {
        return brain.state() == GuardBrain.State.IDLE || brain.state() == GuardBrain.State.PATROLLING;
    }

    private boolean canPerceive(LivingEntity player, boolean tracking) {
        if (!isHostileTarget(player) || !insideTerritory(player.getBlockPos())) return false;
        double detection = loadout == null ? 42 : loadout.detectionRange();
        double range = player.isSneaking() ? Math.min(28, detection) : detection;
        if (squaredDistanceTo(player) > range * range) return false;
        if (!tracking && !GuardBrain.insideFieldOfView(player.getX() - getX(), player.getZ() - getZ(), getHeadYaw(), 0.25)) return false;
        return getVisibilityCache().canSee(player);
    }

    private boolean insideTerritory(BlockPos pos) {
        return GuardTerritory.contains(territory.getX(), territory.getZ(), territoryRadius, squareTerritory, pos.getX(), pos.getZ());
    }

    @Override
    public boolean damage(DamageSource source, float amount) {
        float movementSpeed = limbAnimator.getSpeed();
        float previousHealth = getHealth(), previousAbsorption = getAbsorptionAmount();
        String srcName = source.getName() != null ? source.getName().toLowerCase(java.util.Locale.ROOT) : "";
        if (srcName.contains("explosion")
                || (source.isOf(com.bobincraft.combat.CombatDamage.SHRAPNEL) && amount >= 12.0f)) {
            killedByBlast = true;
        }
        boolean damaged;
        try {
            damaged = super.damage(source, amount);
        } finally {
            // Keep native health, armour, cooldown and threat logic without the artificial limb kick.
            limbAnimator.setSpeed(movementSpeed);
            hurtTime = maxHurtTime = 0;
        }
        if (damaged && getWorld() instanceof ServerWorld world
                && (getHealth() < previousHealth || getAbsorptionAmount() < previousAbsorption)
                && !source.isOf(com.bobincraft.combat.CombatDamage.BLEEDING)
                && !source.isOf(com.bobincraft.combat.CombatDamage.SHRAPNEL))
            com.bobincraft.combat.CombatEffects.blood(world, this, 6);
        if(damaged&&getHealth()<previousHealth&&!getWorld().isClient&&!source.isOf(com.bobincraft.combat.CombatDamage.BLEEDING)){
            retreatTicks=200;
            if(source.getSource()!=null)survivalThreat=source.getSource().getPos();
            else if(source.getAttacker()!=null)survivalThreat=source.getAttacker().getPos();
        }
        if (damaged && !getWorld().isClient && source.getAttacker() instanceof LivingEntity player && isHostileTarget(player)) {
            lastKnownPosition = player.getPos();
            brain.confirmThreat();
            if (age >= nextAlertAge) {
                nextAlertAge = age + 160;
                WarzoneEncounterManager.alertAllies(this, lastKnownPosition);
            }
        }
        return damaged;
    }

    @Override public void onDamaged(DamageSource source) {
        float movementSpeed = limbAnimator.getSpeed();
        super.onDamaged(source); // Retain sound and last-damage information on receiving clients.
        limbAnimator.setSpeed(movementSpeed);
        hurtTime = maxHurtTime = 0;
    }

    @Override public void animateDamage(float yaw) { hurtTime = maxHurtTime = 0; }

    /** Ordinary hits do not jolt the model; explosion pressure applies its own physical impulse. */
    @Override public void takeKnockback(double strength, double x, double z) { }

    @Override public void onDeath(DamageSource source) {
        if (!getWorld().isClient) {
            String srcName = source.getName() != null ? source.getName().toLowerCase(java.util.Locale.ROOT) : "";
            boolean blast = killedByBlast || srcName.contains("explosion");
            boolean wasCrawling = isInjuryProne() && proneStartedAge >= 0 && (age - proneStartedAge) > 2;
            int pose = wasCrawling ? com.bobincraft.combat.GuardInjuryPose.POSE_CRAWL
                    : (blast ? com.bobincraft.combat.GuardInjuryPose.POSE_BLAST
                    : ((injuries.missingLimbs() & com.bobincraft.combat.HitRegion.LEGS) != 0
                            ? com.bobincraft.combat.GuardInjuryPose.POSE_CRAWL
                            : com.bobincraft.combat.GuardInjuryPose.POSE_FALLEN));
            dataTracker.set(MISSING_LIMBS, com.bobincraft.combat.GuardInjuryPose.pack(injuries.missingLimbs(), pose));
        }
        if (getWorld() instanceof ServerWorld world && rosterSlot >= 0 && !isHubGuard())
            CombatGuardRoster.forResident(world, residentialResident).died(siteId, rosterSlot, getUuid());
        super.onDeath(source);
    }

    @Override
    protected void dropLoot(DamageSource damageSource, boolean causedByPlayer) {
        if (getWorld().getServer() == null) return;
        super.dropLoot(damageSource, causedByPlayer);
    }

    @Override
    public boolean canImmediatelyDespawn(double distanceSquared) { return false; }

    @Override
    public void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);
        com.bobincraft.combat.InjuryPersistence.write(injuries, nbt);
        nbt.putInt("BobinLegTrauma",legTraumaTicks);nbt.putInt("BobinRetreatTicks",retreatTicks);
        nbt.putBoolean("BobinGuardConfigured", configured);
        nbt.putBoolean("BobinManualSpawn", manuallySpawned);
        nbt.putInt("BobinSite", siteId);
        nbt.putInt("BobinRosterSlot", rosterSlot);
        nbt.putBoolean("BobinResidentialResident", residentialResident);
        nbt.putLong("BobinPost", post.asLong());
        nbt.putLong("BobinWorkPost", workPost.asLong());
        nbt.putLong("BobinDepot", depot.asLong());
        nbt.putBoolean("BobinDepotConsumed",depotSupply.snapshot().consumed());
        nbt.putInt("BobinDepotWork",depotSupply.snapshot().workTicks());
        nbt.putLong("BobinTerritory", territory.asLong());
        nbt.putInt("BobinRadius", territoryRadius);
        nbt.putBoolean("BobinSquareTerritory", squareTerritory);
        nbt.putString("BobinRole", role.name());
        nbt.putString("BobinFaction", faction().name());
        nbt.putInt("BobinVariant", appearanceVariant());
        nbt.putBoolean("BobinHubGuard", isHubGuard());
        nbt.putString("BobinWeapon", weapon.name());
        nbt.putInt("BobinDanger", danger);
        if (firearm != null) {
            NpcFireControl.Snapshot state = firearm.snapshot();
            NbtCompound gun = new NbtCompound();
            gun.putInt("Rounds", state.rounds());
            gun.putInt("Reserve", state.reserve());
            gun.putInt("Cooldown", state.cooldown());
            gun.putInt("Reloading", state.reloading());
            gun.putInt("Burst", state.burst());
            nbt.put("BobinFirearm", gun);
        }
        NbtList route = new NbtList();
        for (BlockPos waypoint : patrol) {
            NbtCompound entry = new NbtCompound();
            entry.putLong("Pos", waypoint.asLong());
            route.add(entry);
        }
        nbt.put("BobinPatrol", route);
        nbt.putInt("BobinPatrolIndex", patrolIndex);
        GuardDutySchedule.Snapshot shift = duties.snapshot();
        NbtCompound rota = new NbtCompound();
        rota.putString("Assignment", shift.assignment().name());
        rota.putInt("Shift", shift.shiftTicks());
        rota.putInt("RouteIndex", shift.routeIndex());
        rota.putInt("Dwell", shift.dwellTicks());
        nbt.put("BobinDuty", rota);
        nbt.putInt("BobinSearchIndex", searchIndex);
        GuardBrain.Snapshot awareness = brain.snapshot();
        NbtCompound saved = new NbtCompound();
        saved.putString("State", awareness.state().name());
        saved.putInt("Suspicion", awareness.suspicion());
        saved.putInt("Memory", awareness.memoryTicks());
        saved.putInt("Investigation", awareness.investigationTicks());
        if (lastKnownPosition != null) {
            saved.putDouble("LastX", lastKnownPosition.x);
            saved.putDouble("LastY", lastKnownPosition.y);
            saved.putDouble("LastZ", lastKnownPosition.z);
        }
        nbt.put("BobinAwareness", saved);
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        com.bobincraft.combat.InjuryPersistence.read(injuries, nbt);
        legTraumaTicks=com.bobincraft.combat.GuardMobility.savedTicks(nbt.getInt("BobinLegTrauma"));dataTracker.set(LEG_TRAUMA,legTraumaTicks>0);retreatTicks=Math.max(0,Math.min(200,nbt.getInt("BobinRetreatTicks")));
        configured = nbt.getBoolean("BobinGuardConfigured");
        manuallySpawned = nbt.getBoolean("BobinManualSpawn");
        dataTracker.set(HUB_GUARD, nbt.getBoolean("BobinHubGuard"));
        Faction savedFaction;
        try { savedFaction = Faction.valueOf(nbt.getString("BobinFaction")); }
        catch (IllegalArgumentException ignored) { savedFaction = Faction.REGULAR_ARMY; }
        dataTracker.set(FACTION, savedFaction.ordinal());
        dataTracker.set(VARIANT, Math.floorMod(nbt.getInt("BobinVariant"), 3));
        danger = Math.max(0, Math.min(3, nbt.getInt("BobinDanger")));
        weapon = NpcWeapon.fromName(nbt.getString("BobinWeapon"));
        siteId = nbt.getInt("BobinSite");
        rosterSlot = nbt.contains("BobinRosterSlot", NbtElement.INT_TYPE) ? nbt.getInt("BobinRosterSlot") : -1;
        residentialResident = nbt.getBoolean("BobinResidentialResident");
        post = BlockPos.fromLong(nbt.getLong("BobinPost"));
        workPost = nbt.contains("BobinWorkPost", NbtElement.LONG_TYPE) ? BlockPos.fromLong(nbt.getLong("BobinWorkPost")) : post;
        depot = nbt.contains("BobinDepot",NbtElement.LONG_TYPE)?BlockPos.fromLong(nbt.getLong("BobinDepot")):post;
        depotSupply.restore(new GuardDepotSupply.Snapshot(nbt.getBoolean("BobinDepotConsumed"),nbt.getInt("BobinDepotWork")));
        territory = BlockPos.fromLong(nbt.getLong("BobinTerritory"));
        territoryRadius = Math.max(16, Math.min(MAX_TERRITORY_RADIUS, nbt.getInt("BobinRadius")));
        squareTerritory = nbt.contains("BobinSquareTerritory", NbtElement.BYTE_TYPE)
                ? nbt.getBoolean("BobinSquareTerritory") : rosterSlot >= 0;
        try { role = WarzoneBasePlan.SpotRole.valueOf(nbt.getString("BobinRole")); }
        catch (IllegalArgumentException ignored) { role = WarzoneBasePlan.SpotRole.SOLDIER; }
        patrol.clear();
        NbtList route = nbt.getList("BobinPatrol", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < Math.min(16, route.size()); i++) {
            BlockPos waypoint = BlockPos.fromLong(route.getCompound(i).getLong("Pos"));
            if (insideTerritory(waypoint)) patrol.add(waypoint);
        }
        patrolIndex = patrol.isEmpty() ? 0 : Math.floorMod(nbt.getInt("BobinPatrolIndex"), patrol.size());
        if (!insideTerritory(workPost)) workPost = post;
        if (!insideTerritory(depot)) depot = post;
        NbtCompound rota = nbt.getCompound("BobinDuty");
        try {
            duties.restore(new GuardDutySchedule.Snapshot(GuardDutySchedule.Assignment.valueOf(rota.getString("Assignment")),
                    rota.getInt("Shift"), rota.getInt("RouteIndex"), rota.getInt("Dwell")), patrol.size());
        } catch (IllegalArgumentException ignored) {
            duties.assign(role == WarzoneBasePlan.SpotRole.SENTRY ? GuardDutySchedule.Assignment.SENTRY
                    : GuardDutySchedule.Assignment.PATROL, patrolIndex * 317);
        }
        searchIndex = Math.max(0, Math.min(4, nbt.getInt("BobinSearchIndex")));
        NbtCompound saved = nbt.getCompound("BobinAwareness");
        try {
            brain.restore(new GuardBrain.Snapshot(GuardBrain.State.valueOf(saved.getString("State")),
                    saved.getInt("Suspicion"), saved.getInt("Memory"), saved.getInt("Investigation")));
        } catch (IllegalArgumentException ignored) { /* Older saves resume at their assigned post. */ }
        if (saved.contains("LastX", NbtElement.DOUBLE_TYPE)) {
            Vec3d position = new Vec3d(saved.getDouble("LastX"), saved.getDouble("LastY"), saved.getDouble("LastZ"));
            if (insideTerritory(BlockPos.ofFloored(position))) lastKnownPosition = position;
        }
        if (isHubGuard()) assignHubGuard(post);
        else if (configured) {
            boolean migrate = !nbt.contains("BobinWeapon", NbtElement.STRING_TYPE);
            if (migrate && getWorld() instanceof ServerWorld world) {
                var generator = WarzoneDimension.generatorOf(world);
                if (generator != null) for (WarzoneBasePlan.Site site : generator.terrain().basePlan().sites()) {
                    if (site.id != siteId) continue;
                    dataTracker.set(FACTION, site.faction.ordinal());
                    dataTracker.set(VARIANT, Math.floorMod(post.getX() * 17 + post.getZ() * 13 + siteId * 31, 3));
                    danger = FactionLoadouts.danger(site);
                    break;
                }
            }
            configureArmament(migrate);
            if (nbt.contains("BobinFirearm", NbtElement.COMPOUND_TYPE)) {
                NbtCompound gun = nbt.getCompound("BobinFirearm");
                firearm.restore(new NpcFireControl.Snapshot(gun.getInt("Rounds"), gun.getInt("Reserve"),
                        gun.getInt("Cooldown"), gun.getInt("Reloading"), 0, gun.getInt("Burst")));
            }
        }
        setAbleToJoinRaid(false);
        setPersistent();
        refreshInjuries(false);
    }

    private void tickFirearm() {
        if(hasVehicle()){setAttacking(false);return;}
        if(droneThreat!=null&&age<droneMemoryUntil&&(getTarget()==null||droneResponse==DroneThreatPolicy.Response.TAKE_COVER)&&firearm!=null&&loadout!=null){
            boolean clear=NpcWeaponAdapter.canFireDrone(this,droneThreat,weapon);setAttacking(clear);
            if(clear)getLookControl().lookAt(droneThreat,35,50);
            if(firearm.tick(clear))NpcWeaponAdapter.fireDrone(this,droneThreat,weapon,Math.max(1.3,loadout.spreadDegrees()*.6));
            return;
        }
        if ((missingLimbs() & com.bobincraft.combat.HitRegion.ARMS) != 0) { setAttacking(false); return; }
        if (firearm == null || loadout == null) return;
        LivingEntity target = getTarget();
        boolean clear = simulationActive && target != null && insideTerritory(target.getBlockPos()) && NpcWeaponAdapter.canFire(this, target, weapon);
        setAttacking(clear);
        int wasReloading = firearm.snapshot().reloading();
        if (firearm.tick(clear)) {
            double movementSpread = getVelocity().horizontalLengthSquared() > 0.0025 ? 2.5 : 0;
            NpcWeaponAdapter.fire(this, target, weapon, Math.max(2.5, loadout.spreadDegrees() - danger * 0.35) + movementSpread);
        } else if (wasReloading == 0 && firearm.snapshot().reloading() > 0) {
            getWorld().playSound(null, getBlockPos(), SoundEvents.ITEM_ARMOR_EQUIP_IRON,
                    SoundCategory.HOSTILE, 0.55F, 1.1F);
        }
    }

    private final class FirearmApproachGoal extends Goal {
        FirearmApproachGoal() { setControls(EnumSet.of(Control.MOVE, Control.LOOK)); }
        @Override public boolean canStart() { return configured && simulationActive && !isHubGuard() && !hasVehicle() && getTarget() != null; }
        @Override public boolean shouldContinue() { return canStart(); }
        @Override public void stop() { getNavigation().stop(); }
        @Override public void tick() {
            LivingEntity target = getTarget();
            if (target == null) return;
            getLookControl().lookAt(target, 30.0F, 30.0F);
            // Tower guards hold their assigned roof; other roles approach only within their own territory.
            if (role != WarzoneBasePlan.SpotRole.SENTRY && age >= nextRepositionAge
                    && (squaredDistanceTo(target) < 8 * 8 || firearm.snapshot().reloading() > 0)) {
                BlockPos cover = findCover(target);
                nextRepositionAge = age + 100;
                if (cover != null) { nextPathAge = 0; moveTo(cover, 0.95); return; }
            }
            if (role != WarzoneBasePlan.SpotRole.SENTRY && squaredDistanceTo(target) > Math.pow(weapon.range() * 0.8, 2)) {
                moveTo(target.getBlockPos(), 0.9);
            } else if (getNavigation().isIdle() || age >= nextRepositionAge) getNavigation().stop();
        }
    }

    private void moveTo(BlockPos destination, double speed) {
        if (age < nextPathAge || !insideTerritory(destination) || !getWorld().isChunkLoaded(destination)) return;
        if (com.bobincraft.destruction.DestructibleStructureRegistry.isBridgeSectionDestroyedAt(destination.getX(), destination.getZ())) {
            getNavigation().stop();
            blockedPaths++;
            nextPathAge = age + 60;
            return;
        }
        boolean found = getNavigation().startMovingTo(destination.getX() + 0.5, destination.getY(), destination.getZ() + 0.5, speed);
        nextPathAge = age + (found ? 30 : 100);
        blockedPaths = found ? 0 : blockedPaths + 1;
    }

    private final class AssignedPostGoal extends Goal {
        AssignedPostGoal() { setControls(EnumSet.of(Control.MOVE, Control.LOOK)); }
        @Override public boolean canStart() { return configured && simulationActive && !isHubGuard() && !hasVehicle() && getTarget() == null; }
        @Override public boolean shouldContinue() { return canStart(); }
        @Override public void stop() { getNavigation().stop(); }
        @Override public void tick() {
            GuardBrain.State state = brain.state();
            if (state == GuardBrain.State.SEARCHING || state == GuardBrain.State.INVESTIGATING) {
                // A sentry scans from the tower; ground units inspect a finite cross around the last sighting.
                if (lastKnownPosition != null && role != WarzoneBasePlan.SpotRole.SENTRY) {
                    BlockPos known = BlockPos.ofFloored(lastKnownPosition);
                    int[][] offsets = {{0, 0}, {4, 0}, {0, 4}, {-4, 0}, {0, -4}};
                    BlockPos inspect = known.add(offsets[searchIndex][0], 0, offsets[searchIndex][1]);
                    if (!insideTerritory(inspect)) inspect = post;
                    if (squaredDistanceTo(Vec3d.ofBottomCenter(inspect)) < 4) {
                        getNavigation().stop();
                        getLookControl().lookAt(lastKnownPosition.x, lastKnownPosition.y + 1.3, lastKnownPosition.z);
                        if (age % 40 == 0) searchIndex = (searchIndex + 1) % offsets.length;
                    } else moveTo(inspect, 0.8);
                } else if (lastKnownPosition != null)
                    getLookControl().lookAt(lastKnownPosition.x, lastKnownPosition.y + 1.3, lastKnownPosition.z);
            } else if (state == GuardBrain.State.RETURNING_TO_POST) {
                moveTo(post, 0.8);
            } else if (isCalm()) {
                tickDuty();
            } else if (state == GuardBrain.State.IDLE || state == GuardBrain.State.SUSPICIOUS) {
                getNavigation().stop();
                if (lastKnownPosition != null)
                    getLookControl().lookAt(lastKnownPosition.x, lastKnownPosition.y + 1.3, lastKnownPosition.z);
            }
        }
    }

    private void tickDuty() {
        if (rosterSlot>=0 && firearm!=null && !depotSupply.consumed()) {
            var ammunition=firearm.snapshot();
            if (ammunition.rounds()==0 && ammunition.reserve()==0) {
                boolean arrived=squaredDistanceTo(Vec3d.ofBottomCenter(depot))<2.25;
                if(!arrived)moveTo(depot,.55);else {
                    getNavigation().stop();
                    getLookControl().lookAt(depot.getX()+.5,depot.getY()+.3,depot.getZ()+1.5);
                    if(age%40==0)swingHand(net.minecraft.util.Hand.MAIN_HAND);
                }
                if(depotSupply.tick(true,arrived,true))firearm.resupplyMagazine();
                return;
            }
        }
        GuardDutySchedule.Activity activity = duties.activity();
        if (activity == GuardDutySchedule.Activity.PATROL && !patrol.isEmpty()) {
            BlockPos waypoint = patrol.get(Math.floorMod(duties.routeIndex(), patrol.size()));
            if (squaredDistanceTo(Vec3d.ofBottomCenter(waypoint)) < 3.0) {
                getNavigation().stop();
                duties.arrived(patrol.size());
                lookAlongRoute();
            } else {
                duties.enRoute();
                moveTo(waypoint, 0.55);
                // A blocked player-built path is not a reason to retry the same node forever.
                if (blockedPaths >= 3) {
                    for (int i = 0; i < 80; i++) duties.arrived(patrol.size());
                    blockedPaths = 0;
                }
            }
            return;
        }
        // Work focus may be a solid crate or radio: stand at the authored walkable post.
        BlockPos station = post;
        if (squaredDistanceTo(Vec3d.ofBottomCenter(station)) > 2.25) { moveTo(station, 0.5); return; }
        getNavigation().stop();
        if (activity == GuardDutySchedule.Activity.WORK) {
            // Inspect a supply stack, repair bench or radio at the planned work station.
            getLookControl().lookAt(workPost.getX() + 0.5, workPost.getY() + 0.4, workPost.getZ() + 0.5);
            if (age >= nextWorkAnimation) {
                swingHand(net.minecraft.util.Hand.MAIN_HAND);
                nextWorkAnimation = age + 100 + Math.floorMod(getUuid().hashCode(), 60);
            }
        } else lookAlongRoute();
    }

    private void lookAlongRoute() {
        // Fixed sectors make the watch readable without random head spinning.
        int sector = Math.floorMod(age / 100 + getUuid().hashCode(), 4);
        int[][] directions = {{8, 0}, {0, 8}, {-8, 0}, {0, -8}};
        getLookControl().lookAt(getX() + directions[sector][0], getEyeY(), getZ() + directions[sector][1]);
    }

    private BlockPos findCover(LivingEntity threat) {
        if (role == WarzoneBasePlan.SpotRole.SENTRY) return null;
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        for (int dx : new int[]{-6, -3, 0, 3, 6}) for (int dz : new int[]{-6, -3, 0, 3, 6}) {
            if (dx == 0 && dz == 0) continue;
            BlockPos feet = getBlockPos().add(dx, 0, dz);
            if (!insideTerritory(feet) || !getWorld().isChunkLoaded(feet)
                    || getWorld().getBlockState(feet.down()).getCollisionShape(getWorld(), feet.down()).isEmpty()
                    || !getWorld().getFluidState(feet).isEmpty()
                    || !getWorld().isSpaceEmpty(this, new net.minecraft.util.math.Box(feet.getX() + 0.2, feet.getY(), feet.getZ() + 0.2,
                    feet.getX() + 0.8, feet.getY() + 1.95, feet.getZ() + 0.8))) continue;
            Vec3d eye = Vec3d.ofBottomCenter(feet).add(0, getStandingEyeHeight(), 0);
            if (getWorld().raycast(new RaycastContext(threat.getEyePos(), eye, RaycastContext.ShapeType.COLLIDER,
                    RaycastContext.FluidHandling.NONE, this)).getType() == HitResult.Type.MISS) continue;
            double distance = squaredDistanceTo(Vec3d.ofBottomCenter(feet));
            if (distance < bestDistance) { best = feet; bestDistance = distance; }
        }
        return best;
    }

    private boolean wounded(){return injuries.isBleeding()||missingLimbs()!=0||isInjuryProne()||retreatTicks>0||getHealth()<getMaxHealth()*.45;}

    private void perceiveDrones(ServerWorld world){
        droneThreat=null;droneResponse=DroneThreatPolicy.Response.IGNORE;double best=49*49;
        if(world.getDifficulty()==net.minecraft.world.Difficulty.PEACEFUL)return;
        for(var drone:world.getEntitiesByClass(com.bobincraft.drone.DroneEntity.class,getBoundingBox().expand(48),e->e.isAlive())){
            double distance=squaredDistanceTo(drone);if(distance>=best)continue;
            var owner=drone.getOwnerUuid()==null?null:world.getServer().getPlayerManager().getPlayer(drone.getOwnerUuid());
            boolean visible=world.raycast(new RaycastContext(getEyePos(),drone.getBoundingBox().getCenter(),RaycastContext.ShapeType.COLLIDER,RaycastContext.FluidHandling.NONE,this)).getType()==HitResult.Type.MISS;
            boolean approaching=drone.getVelocity().dotProduct(getPos().subtract(drone.getPos()))>.12;
            var response=DroneThreatPolicy.response(owner!=null&&validTarget(owner),drone.isArmed(),visible,Math.sqrt(distance),approaching,wounded());
            if(response==DroneThreatPolicy.Response.IGNORE)continue;
            best=distance;droneResponse=response;droneThreat=drone;droneMemoryUntil=age+60;survivalThreat=drone.getPos();
            brain.hearNoise();if(visible)getLookControl().lookAt(drone,30,50);
            if(age>=nextAlertAge&&drone.isArmed()){nextAlertAge=age+160;WarzoneEncounterManager.alertAllies(this,drone.getPos());}
        }
    }
    public net.minecraft.entity.Entity vehicleThreat(){
        if(droneThreat!=null&&age<droneMemoryUntil&&NpcWeaponAdapter.canFireDrone(this,droneThreat,weapon))return droneThreat;
        return getTarget()!=null&&isHostileTarget(getTarget())&&getVisibilityCache().canSee(getTarget())?getTarget():null;
    }
    public Vec3d vehiclePatrolPoint(){
        if(patrol.isEmpty()||role==WarzoneBasePlan.SpotRole.SENTRY)return null;
        BlockPos point=patrol.get(Math.floorMod(patrolIndex,patrol.size()));if(squaredDistanceTo(Vec3d.ofBottomCenter(point))<49){patrolIndex=(patrolIndex+1)%patrol.size();point=patrol.get(patrolIndex);}
        return Vec3d.ofBottomCenter(point);
    }
    private BlockPos survivalCover(Vec3d threat){
        BlockPos best=null;double score=Double.POSITIVE_INFINITY;
        List<BlockPos> candidates=new ArrayList<>();
        for(int dx:new int[]{-12,-8,-4,0,4,8,12})for(int dz:new int[]{-12,-8,-4,0,4,8,12})for(int dy:new int[]{0,-1,-2,1,2,-3,-4})candidates.add(getBlockPos().add(dx,dy,dz));
        if(getWorld() instanceof ServerWorld world){var generator=WarzoneDimension.generatorOf(world);
            if(generator!=null&&generator.terrain().generationVersion()>=9)for(var site:generator.terrain().zonePlan().sites())if(site.id()==siteId){
                int cx=site.x(),cz=site.entrance().z()-8;var layout=com.bobincraft.warzone.OccupationPlan.trench(site);
                List<BlockPos> hints=new ArrayList<>();hints.add(new BlockPos(cx-12,generator.terrain().height(cx-12,cz-6)+1,cz-6));
                if(layout!=null){int feet=com.bobincraft.warzone.OccupationPlan.trenchFloor(generator.terrain(),layout)+1;
                    hints.add(new BlockPos(layout.x()+2,feet,layout.z()));if(layout.dugout())hints.add(new BlockPos(layout.x()+7,feet,layout.z()-7));}
                for(var hint:hints)for(int dy:new int[]{0,1,-1,2,-2})candidates.add(hint.up(dy));
            }
        }
        for(BlockPos feet:candidates){if(!insideTerritory(feet)||!getWorld().isChunkLoaded(feet)||!getWorld().getFluidState(feet).isEmpty())continue;
            if(getWorld().getBlockState(feet.down()).getCollisionShape(getWorld(),feet.down()).isEmpty())continue;
            Vec3d point=Vec3d.ofBottomCenter(feet);var box=isInjuryProne()?com.bobincraft.combat.GuardInjuryPose.bounds(point,getBodyYaw()):getDimensions(net.minecraft.entity.EntityPose.STANDING).getBoxAt(point);
            if(!getWorld().isSpaceEmpty(this,box))continue;
            boolean covered=threat!=null&&getWorld().raycast(new RaycastContext(threat,point.add(0,isInjuryProne()?.5:1.3,0),RaycastContext.ShapeType.COLLIDER,RaycastContext.FluidHandling.NONE,this)).getType()!=HitResult.Type.MISS;
            double distance=squaredDistanceTo(point),value=distance+(covered?0:350)+(threat==null?feet.getSquaredDistance(post)*.2:Math.max(0,12-point.distanceTo(threat))*12);
            if(value<score){var path=getNavigation().findPathTo(feet,0);if(path==null||!path.reachesTarget())continue;score=value;best=feet;}
        }
        return best;
    }
    /** Wounded guards leave exposed posts, walking or crawling with their existing injury movement penalty. */
    private final class SurvivalGoal extends Goal {
        private int nextCheck,until;private BlockPos cover;
        SurvivalGoal(){setControls(EnumSet.of(Control.MOVE));}
        private boolean danger(){return wounded()||droneResponse==DroneThreatPolicy.Response.TAKE_COVER;}
        @Override public boolean canStart(){
            if(!configured||!simulationActive||isHubGuard()||!danger()||age<nextCheck)return false;
            if(getVehicle() instanceof com.bobincraft.vehicle.ArmoredVehicleEntity armor&&armor.armorHealth()>0&&canUseHands()&&!isInjuryProne())return false;
            if(hasVehicle())stopRiding();nextCheck=age+80;
            Vec3d threat=droneThreat!=null?droneThreat.getPos():getTarget()!=null?getTarget().getEyePos():survivalThreat!=null?survivalThreat:lastKnownPosition;
            cover=survivalCover(threat);return cover!=null;
        }
        @Override public void start(){until=age+200;nextPathAge=0;moveTo(cover,isInjuryProne()?1.0:.85);}
        @Override public boolean shouldContinue(){return simulationActive&&danger()&&age<until;}
        @Override public void tick(){if(squaredDistanceTo(Vec3d.ofBottomCenter(cover))<1.5)getNavigation().stop();else moveTo(cover,isInjuryProne()?1.0:.85);}
        @Override public void stop(){getNavigation().stop();}
    }
    private final class VehicleOperatorGoal extends Goal {
        private com.bobincraft.vehicle.ArmoredVehicleEntity vehicle;private int nextCheck,deadline;private Vec3d boarding;
        VehicleOperatorGoal(){setControls(EnumSet.of(Control.MOVE,Control.LOOK));}
        @Override public boolean canStart(){
            if(!configured||!simulationActive||isHubGuard()||!canUseHands()||isInjuryProne())return false;
            if(getVehicle() instanceof com.bobincraft.vehicle.ArmoredVehicleEntity current){vehicle=current;return true;}
            if(wounded())return false;
            if(age<nextCheck)return false;nextCheck=age+100;
            vehicle=getWorld().getEntitiesByClass(com.bobincraft.vehicle.ArmoredVehicleEntity.class,getBoundingBox().expand(32),v->v.siteId()==siteId&&v.canOperate(WarzoneGuardEntity.this)&&v.ammo()>0).stream().min(java.util.Comparator.comparingDouble(WarzoneGuardEntity.this::squaredDistanceTo)).orElse(null);
            if(vehicle==null||!vehicle.reserveCrew(WarzoneGuardEntity.this))return false;
            boarding=null;
            for(Vec3d candidate:vehicle.boardingPoints(WarzoneGuardEntity.this)){
                var path=getNavigation().findPathTo(BlockPos.ofFloored(candidate),0);
                if(path!=null&&path.reachesTarget()){boarding=candidate;break;}
            }
            if(boarding==null){vehicle.releaseCrew(WarzoneGuardEntity.this);return false;}
            deadline=age+400;return true;
        }
        @Override public boolean shouldContinue(){return simulationActive&&canUseHands()&&!isInjuryProne()&&vehicle!=null&&vehicle.isAlive()&&(getVehicle()==vehicle||!wounded()&&age<deadline&&vehicle.canOperate(WarzoneGuardEntity.this));}
        @Override public void tick(){
            if(getVehicle()==vehicle){getNavigation().stop();return;}
            if(!vehicle.reserveCrew(WarzoneGuardEntity.this)||boarding==null)return;
            Vec3d point=boarding;if(squaredDistanceTo(point)<2.25){getNavigation().stop();if(startRiding(vehicle))vehicle.releaseCrew(WarzoneGuardEntity.this);return;}
            if(age>=nextPathAge){getNavigation().startMovingTo(point.x,point.y,point.z,.7);nextPathAge=age+30;}
        }
        @Override public void stop(){getNavigation().stop();if(vehicle!=null)vehicle.releaseCrew(WarzoneGuardEntity.this);if(getVehicle()==vehicle&&(!canUseHands()||isInjuryProne()))stopRiding();vehicle=null;boarding=null;}
    }
}
