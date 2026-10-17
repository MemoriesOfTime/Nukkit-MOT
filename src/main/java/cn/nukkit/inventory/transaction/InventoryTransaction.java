package cn.nukkit.inventory.transaction;

import cn.nukkit.Player;
import cn.nukkit.Server;
import cn.nukkit.block.BlockID;
import cn.nukkit.event.inventory.InventoryClickEvent;
import cn.nukkit.event.inventory.InventoryTransactionEvent;
import cn.nukkit.inventory.Inventory;
import cn.nukkit.inventory.BaseInventory;
import cn.nukkit.inventory.DoubleChestInventory;
import cn.nukkit.inventory.BundleInventory;
import cn.nukkit.inventory.InventoryMutationGuard;
import cn.nukkit.inventory.PlayerUIComponent;
import cn.nukkit.inventory.PlayerInventory;
import cn.nukkit.inventory.ShulkerBoxInventory;
import cn.nukkit.inventory.transaction.action.CreativeInventoryAction;
import cn.nukkit.inventory.transaction.action.DropItemAction;
import cn.nukkit.entity.item.EntityItem;
import cn.nukkit.inventory.transaction.action.InventoryAction;
import cn.nukkit.inventory.transaction.action.SlotChangeAction;
import cn.nukkit.item.Item;
import cn.nukkit.item.ItemDye;
import cn.nukkit.item.enchantment.Enchantment;
import cn.nukkit.network.protocol.ProtocolInfo;

import java.util.*;
import java.util.regex.Pattern;

/**
 * @author CreeperFace
 */
public class InventoryTransaction {

    protected boolean invalid;
    protected boolean hasExecuted;

    protected Player source;

    protected Set<Inventory> inventories = new HashSet<>();

    protected List<InventoryAction> actions = new ArrayList<>();

    public InventoryTransaction(Player source, List<InventoryAction> actions) {
        this(source, actions, true);
    }

    public InventoryTransaction(Player source, List<InventoryAction> actions, boolean init) {
        if (init) {
            init(source, actions);
        }
    }

    protected void init(Player source, List<InventoryAction> actions) {
        //creationTime = System.currentTimeMillis();
        this.source = source;

        for (InventoryAction action : actions) {
            this.addAction(action);
        }
    }

    public Player getSource() {
        return source;
    }

    public long getCreationTime() {
        return 0; // unused
    }

    public Set<Inventory> getInventories() {
        return inventories;
    }

    public List<InventoryAction> getActionList() {
        return actions;
    }

    public Set<InventoryAction> getActions() {
        return new HashSet<>(actions);
    }

    public void addAction(InventoryAction action) {
        if (invalid) {
            Server.getInstance().getLogger().debug("Failed to add InventoryAction for " + source.getName() + ": previous run was marked as invalid");
            return;
        }

        if (action instanceof SlotChangeAction slotChangeAction) {
            Item targetItem = slotChangeAction.getTargetItem();
            Item sourceItem = slotChangeAction.getSourceItem();
            if (targetItem.getCount() > targetItem.getMaxStackSize() || sourceItem.getCount() > sourceItem.getMaxStackSize()) {
                invalid = true;
                Server.getInstance().getLogger().debug("Failed to add SlotChangeAction for " + source.getName() + ": illegal item stack size");
                return;
            }

            if (!slotChangeAction.getInventory().allowedToAdd(targetItem)) {
                invalid = true;
                Server.getInstance().getLogger().debug("Failed to add SlotChangeAction for " + source.getName() + ": " + slotChangeAction.getInventory().getName() + " inventory doesn't allow item " + targetItem.getId());
                return;
            }

            if (!source.isCreative()) {
                int slot = slotChangeAction.getSlot();
                if (slotChangeAction.getInventory() == source.getInventory()
                        && (slot == 36 || slot == 37 || slot == 38 || slot == 39)) {
                    if (sourceItem.hasEnchantment(Enchantment.ID_BINDING_CURSE)) {
                        invalid = true;
                        Server.getInstance().getLogger().debug("Failed to add SlotChangeAction for " + source.getName() + ": armor has binding curse");
                        return;
                    }
                }
            }

            ListIterator<InventoryAction> iterator = this.actions.listIterator();

            while (iterator.hasNext()) {
                InventoryAction existingAction = iterator.next();
                if (existingAction instanceof SlotChangeAction existingSlotChangeAction) {
                    if (slotChangeAction.getInventory() instanceof ShulkerBoxInventory && (targetItem.getId() == BlockID.SHULKER_BOX || targetItem.getId() == BlockID.UNDYED_SHULKER_BOX)) {
                        invalid = true;
                        return;
                    }
                    if (!existingSlotChangeAction.getInventory().equals(slotChangeAction.getInventory())
                            || existingSlotChangeAction.getSlot() != slotChangeAction.getSlot()) {
                        continue;
                    }
                    Item existingSource = existingSlotChangeAction.getSourceItem();
                    Item existingTarget = existingSlotChangeAction.getTargetItem();
                    if (existingSlotChangeAction.getSlot() == slotChangeAction.getSlot() && slotChangeAction.getSourceItem().equals(existingTarget, existingTarget.hasMeta(), existingTarget.hasCompoundTag())) {
                        iterator.set(new SlotChangeAction(existingSlotChangeAction.getInventory(), existingSlotChangeAction.getSlot(), existingSlotChangeAction.getSourceItem(), slotChangeAction.getTargetItem()));
                        action.onAddToTransaction(this);
                        return;
                    } else if (existingSlotChangeAction.getSlot() == slotChangeAction.getSlot()
                            && slotChangeAction.getSourceItem().equals(existingSource, existingSource.hasMeta(), existingSource.hasCompoundTag())
                            && slotChangeAction.getTargetItem().equals(existingTarget, existingTarget.hasMeta(), existingTarget.hasCompoundTag())) {
                        existingSource.setCount(existingSource.getCount() + slotChangeAction.getSourceItem().getCount());
                        existingTarget.setCount(existingTarget.getCount() + slotChangeAction.getTargetItem().getCount());
                        iterator.set(new SlotChangeAction(existingSlotChangeAction.getInventory(), existingSlotChangeAction.getSlot(), existingSource, existingTarget));
                        return;
                    }
                }
            }
        }

        this.actions.add(action);
        action.onAddToTransaction(this);
    }

    /**
     * This method should not be used by plugins, it's used to add tracked inventories for InventoryActions
     * involving inventories.
     *
     * @param inventory to add
     */
    public void addInventory(Inventory inventory) {
        this.inventories.add(inventory);
    }

    /**
     * Replaces every executable occurrence of a pre-event workstation output.
     * Events are fired after the network action list is built, so changing only
     * the event item would otherwise leave the old clone to be delivered.
     *
     * @return a detached authoritative output, or {@code null} for an invalid event output
     */
    protected final Item applyEventOutputToActions(Item originalOutput, Item eventOutput) {
        if (originalOutput == null || eventOutput == null || eventOutput.isNull()) {
            return null;
        }

        Item authoritativeOutput = eventOutput.clone();
        for (InventoryAction action : this.actions) {
            if (action.getSourceItem().equalsExact(originalOutput)) {
                action.setSourceItem(authoritativeOutput.clone());
            }
            if (action.getTargetItem().equalsExact(originalOutput)) {
                action.setTargetItem(authoritativeOutput.clone());
            }
        }
        return authoritativeOutput;
    }

    private static final Pattern SHARED_TRIM_PATTERN = Pattern.compile("^minecraft:[a-z_]+_smithing_template$");
    // Preserve the protected instance field for already compiled plugin subclasses.
    protected final Pattern TRIM_PATTERN = SHARED_TRIM_PATTERN;

    protected boolean matchItems(boolean clientAuthTrim, boolean clientAuthLapis) {
        return matchItems(clientAuthTrim, clientAuthLapis, this.actions);
    }

    private boolean matchItems(boolean clientAuthTrim, boolean clientAuthLapis, List<InventoryAction> matchedActions) {
        List<Item> haveItems = new ArrayList<>();
        List<Item> needItems = new ArrayList<>();

        for (InventoryAction action : matchedActions) {
            if (action.getTargetItem().getId() != Item.AIR) {
                needItems.add(action.getTargetItem());
            }

            if (clientAuthTrim && action instanceof SlotChangeAction slotChangeAction) {
                slotChangeAction.setSmithingClientAuth(true);
            }

            if (!action.isValid(this.source)) {
                invalid = true;
                Server.getInstance().getLogger().debug("matchItems: action.isValid() failed for " + action);
                return false;
            }

            if (action.getSourceItem().getId() != Item.AIR) {
                haveItems.add(action.getSourceItem());
            }
        }

        for (Item needItem : new ArrayList<>(needItems)) {
            for (Item haveItem : new ArrayList<>(haveItems)) {
                if (needItem.equals(haveItem)) {
                    int amount = Math.min(haveItem.getCount(), needItem.getCount());
                    needItem.setCount(needItem.getCount() - amount);
                    haveItem.setCount(haveItem.getCount() - amount);
                    if (haveItem.getCount() == 0) {
                        haveItems.remove(haveItem);
                    }
                    if (needItem.getCount() == 0) {
                        needItems.remove(needItem);
                        break;
                    }
                }
            }
        }

        if (clientAuthLapis) {
            haveItems.removeIf(item -> item.getId() == Item.DYE && item.getDamage() == ItemDye.LAPIS_LAZULI);
        }

        if (clientAuthTrim) {
            needItems.removeIf(item -> TRIM_PATTERN.matcher(item.getNamespaceId()).matches() && item.getDamage() == 0 && item.getCount() <= 1);
        }

        if (!haveItems.isEmpty() || !needItems.isEmpty()) {
            Server.getInstance().getLogger().debug("matchItems failed: unmatched haveItems=" + haveItems + ", needItems=" + needItems);
        }

        return haveItems.isEmpty() && needItems.isEmpty();
    }

    protected void sendInventories() {
        sendInventories(this.actions, this.inventories);
    }

    private void sendInventories(List<InventoryAction> sentActions, Set<Inventory> sentInventories) {
        if (this.getSource().protocol >= ProtocolInfo.v1_16_0) {
            for (InventoryAction action : sentActions) {
                if (action instanceof SlotChangeAction) {
                    SlotChangeAction sca = (SlotChangeAction) action;
                    sca.getInventory().sendSlot(sca.getSlot(), this.source);
                }
            }
        } else {
            for (Inventory inventory : sentInventories) {
                inventory.sendContents(this.source);
                if (inventory instanceof PlayerInventory) {
                    ((PlayerInventory) inventory).sendArmorContents(this.source);
                }
            }
        }
    }

    public boolean canExecute() {
        return matchItems(false, false) && !this.invalid && !this.actions.isEmpty();
    }

    protected boolean callExecuteEvent() {
        return callExecuteEvents(this);
    }

    public static boolean callExecuteEvents(InventoryTransaction transaction) {
        return callExecuteEvents(transaction, null);
    }

    private static boolean callExecuteEvents(InventoryTransaction transaction, List<InventoryAction> eventActions) {
        InventoryTransactionEvent ev = new InventoryTransactionEvent(transaction);
        transaction.source.getServer().getPluginManager().callEvent(ev);

        SlotChangeAction from = null;
        SlotChangeAction to = null;
        Player who = null;

        // Legacy events may author the list; detach it after the event before invoking inventory hooks.
        if (eventActions == null) eventActions = List.copyOf(transaction.actions);
        for (InventoryAction action : eventActions) {
            if (!(action instanceof SlotChangeAction)) {
                continue;
            }
            SlotChangeAction slotChange = (SlotChangeAction) action;

            if (slotChange.getInventory().getHolder() instanceof Player) {
                who = (Player) slotChange.getInventory().getHolder();
            }

            if (from == null) {
                from = slotChange;
            } else {
                to = slotChange;
            }
        }

        if (who != null && to != null) {
            if (from.getTargetItem().getCount() > from.getSourceItem().getCount()) {
                from = to;
            }

            InventoryClickEvent ev2 = new InventoryClickEvent(who, from.getInventory(), from.getSlot(), from.getSourceItem(), from.getTargetItem());
            transaction.source.getServer().getPluginManager().callEvent(ev2);

            if (ev2.isCancelled()) {
                return false;
            }
        }

        return !ev.isCancelled();
    }

    public boolean execute() {
        List<InventoryAction> initialActions = List.copyOf(this.actions);
        boolean containsDrop = initialActions.stream().anyMatch(action -> action instanceof DropItemAction);
        boolean containsOther = initialActions.stream().anyMatch(action -> action.getClass() != DropItemAction.class);
        if (containsDrop && containsOther) {
            if (this.getClass() == InventoryTransaction.class
                    && initialActions.stream().anyMatch(action -> action.getClass() == SlotChangeAction.class)
                    && initialActions.stream().allMatch(action -> action.getClass() == DropItemAction.class
                    || action.getClass() == SlotChangeAction.class
                    && ((SlotChangeAction) action).getInventory() instanceof BaseInventory)) {
                return executePreparedWorldDrop(initialActions);
            }
            // Custom/workstation/world combinations have no preparation contract and cannot publish during rollback.
            this.sendInventories(initialActions, Set.copyOf(this.inventories));
            return false;
        }
        // Exact creative actions are virtual credits/sinks; custom/workstation/world actions have separate effects.
        if (this.getClass() == InventoryTransaction.class
                && this.actions.stream().anyMatch(action -> action.getClass() == SlotChangeAction.class)
                && this.actions.stream().allMatch(action -> action.getClass() == CreativeInventoryAction.class
                || action.getClass() == SlotChangeAction.class
                && ((SlotChangeAction) action).getInventory() instanceof BaseInventory)) {
            return executeSlotTransfer();
        }
        if (this.hasExecuted() || !this.canExecute()) {
            this.sendInventories();
            return false;
        }


        if (!callExecuteEvent()) {
            this.sendInventories();
            return true;
        }

        for (InventoryAction action : this.actions) {
            if (!action.onPreExecute(this.source)) {
                this.sendInventories();
                return true;
            }
            if (action instanceof SlotChangeAction) {
                if (!source.isCreative()) {
                    int slot = ((SlotChangeAction) action).getSlot();
                    if (((SlotChangeAction) action).getInventory() == source.getInventory()
                            && (slot == 36 || slot == 37 || slot == 38 || slot == 39)) {
                        if (action.getSourceItem().hasEnchantment(Enchantment.ID_BINDING_CURSE)) {
                            this.sendInventories();
                            return false;
                        }
                    }
                }
            }
        }

        for (InventoryAction action : this.actions) {
            if (action.execute(this.source)) {
                action.onExecuteSuccess(this.source);
            } else {
                action.onExecuteFail(this.source);
            }
        }

        this.hasExecuted = true;
        return true;
    }

    @SuppressWarnings("try")
    private boolean executePreparedWorldDrop(List<InventoryAction> frozenActions) {
        Set<Inventory> frozenInventories = Collections.newSetFromMap(new IdentityHashMap<>());
        frozenInventories.addAll(this.inventories);
        Map<InventoryAction, WorldActionSnapshot> snapshots = new IdentityHashMap<>();
        for (InventoryAction action : frozenActions) {
            snapshots.put(action, new WorldActionSnapshot(action.getSourceItem(), action.getTargetItem()));
        }
        WorldActorSnapshot actorContext = new WorldActorSnapshot(this.source);
        Player actor = actorContext.actor();
        Set<Inventory> capturedInventories = Collections.newSetFromMap(new IdentityHashMap<>());
        capturedInventories.addAll(frozenInventories);
        for (Inventory inventory : Arrays.asList(actor.getInventory(), actor.getOffhandInventory(),
                actor.getEnderChestInventory(), actor.getUIInventory())) {
            if (inventory != null) capturedInventories.add(inventory);
        }
        List<SlotChangeAction> writes = frozenActions.stream().filter(action -> action.getClass() == SlotChangeAction.class)
                .map(action -> (SlotChangeAction) action).toList();
        List<EntityItem.PreparedDrop> tickets = new ArrayList<>();
        Map<BaseInventory, Map<Integer, Item>> before = new IdentityHashMap<>();
        boolean committed = false;
        try (InventoryMutationGuard.Lease claim = InventoryMutationGuard.tryClaim(capturedInventories)) {
            if (claim == null) return false;
            for (SlotChangeAction action : writes) {
                if (!claim.contains(action.getInventory())) return false;
            }
            for (Inventory inventory : capturedInventories) {
                if (!(inventory instanceof BaseInventory nativeInventory)) return false;
                captureSlotTransfer(nativeInventory, before);
            }
            InventoryMutationGuard.NativeMetadataSnapshot metadata = claim.captureNativeMetadata();
            Map<BaseInventory, Map<Integer, Item>> expectedBackings = expectedWorldBackings(before, writes, snapshots);
            try (InventoryMutationGuard.WriteScope blocked = claim.blockWrites()) {
                try {
                    if (this.hasExecuted() || !worldActorIntact(actorContext)
                            || !matchItems(false, false, frozenActions) || this.invalid
                            || !metadata.matches() || !worldActionsIntact(frozenActions, frozenInventories, snapshots)) {
                        return rejectPreparedWorldDrop(tickets, before, metadata, frozenActions, capturedInventories);
                    }
                    if (!callExecuteEvents(this, frozenActions)
                            || !metadata.matches() || !worldActionsIntact(frozenActions, frozenInventories, snapshots)) {
                        return rejectPreparedWorldDrop(tickets, before, metadata, frozenActions, capturedInventories);
                    }
                    for (InventoryAction action : frozenActions) {
                        if (!action.onPreExecute(actor) || !metadata.matches() || !worldActionsIntact(frozenActions, frozenInventories, snapshots)) {
                            return rejectPreparedWorldDrop(tickets, before, metadata, frozenActions, capturedInventories);
                        }
                        if (action instanceof SlotChangeAction change) {
                            Item target = snapshots.get(action).target();
                            if (!target.isNull() && (target.getCount() <= 0 || target.getCount()
                                    > Math.min(change.getInventory().getMaxStackSize(), target.getMaxStackSize()))
                                    || !actor.isCreative() && change.getSlot() >= 36 && change.getSlot() <= 39
                                    && snapshots.get(action).source().hasEnchantment(Enchantment.ID_BINDING_CURSE)) {
                                return rejectPreparedWorldDrop(tickets, before, metadata, frozenActions, capturedInventories);
                            }
                        }
                    }
                    for (InventoryAction action : frozenActions) {
                        if (!(action instanceof DropItemAction drop)) continue;
                        Item target = snapshots.get(action).target();
                        if (target.getId() == Item.AIR) continue; // Preserve a neutral virtual no-op.
                        if (target.getCount() <= 0) {
                            return rejectPreparedWorldDrop(tickets, before, metadata, frozenActions, capturedInventories);
                        }
                        EntityItem.PreparedDrop ticket = drop.prepare(actor);
                        if (ticket == null) return rejectPreparedWorldDrop(tickets, before, metadata, frozenActions, capturedInventories);
                        tickets.add(ticket);
                    }
                    if (!matchItems(false, false, frozenActions) || this.invalid
                            || !matchesWorldBackings(before) || !metadata.matches() || !worldActionsIntact(frozenActions, frozenInventories, snapshots)
                            || !worldActorIntact(actorContext)
                            || !tickets.stream().allMatch(EntityItem.PreparedDrop::isValid)) {
                        return rejectPreparedWorldDrop(tickets, before, metadata, frozenActions, capturedInventories);
                    }
                    for (SlotChangeAction action : writes) {
                        Item target = snapshots.get(action).target();
                        try (InventoryMutationGuard.WriteScope write = claim.expectWrite(action.getInventory(), action.getSlot(), target)) {
                            if (!action.executeInventoryWrite(actor) || !acceptedSlotTarget(action, target)) {
                                return rejectPreparedWorldDrop(tickets, before, metadata, frozenActions, capturedInventories);
                            }
                        }
                    }
                    for (SlotChangeAction action : writes) {
                        if (!acceptedSlotTarget(action, snapshots.get(action).target())) {
                            return rejectPreparedWorldDrop(tickets, before, metadata, frozenActions, capturedInventories);
                        }
                    }
                    // Native callbacks and the last postcondition read precede the irreversible world publication.
                    if (!metadata.matches() || !matchesWorldBackings(expectedBackings)
                            || !worldActionsIntact(frozenActions, frozenInventories, snapshots)
                            || !worldActorIntact(actorContext)
                            || !tickets.stream().allMatch(EntityItem.PreparedDrop::isValid)) {
                        return rejectPreparedWorldDrop(tickets, before, metadata, frozenActions, capturedInventories);
                    }
                    this.hasExecuted = true;
                    committed = true;
                    for (EntityItem.PreparedDrop ticket : tickets) ticket.release();
                } catch (RuntimeException | Error failure) {
                    if (!committed) {
                        try { discardPreparedWorldDrops(tickets); } catch (RuntimeException | Error cleanup) {
                            if (cleanup != failure) failure.addSuppressed(cleanup);
                        }
                        metadata.restore();
                        restoreSlotTransfer(before);
                        try { this.resyncWorldInventories(frozenActions, capturedInventories); } catch (RuntimeException | Error update) {
                            if (update != failure) failure.addSuppressed(update);
                        }
                    }
                    throw failure;
                }
            }
            // Notification may transfer a released source to a recipient or fail after publication. Never refund here.
            Throwable notificationFailure = null;
            for (EntityItem.PreparedDrop ticket : tickets) {
                try { ticket.notifyPublished(); } catch (RuntimeException | Error failure) {
                    notificationFailure = collectWorldNotificationFailure(notificationFailure, failure);
                }
            }
            for (SlotChangeAction action : writes) {
                try { action.onInventoryWriteCommitted(actor); action.onExecuteSuccess(actor); } catch (RuntimeException | Error failure) {
                    notificationFailure = collectWorldNotificationFailure(notificationFailure, failure);
                }
            }
            try { actor.setUsingItem(false); } catch (RuntimeException | Error failure) {
                notificationFailure = collectWorldNotificationFailure(notificationFailure, failure);
            }
            if (notificationFailure instanceof RuntimeException failure) throw failure;
            if (notificationFailure instanceof Error failure) throw failure;
            return true;
        }
    }

    private static Throwable collectWorldNotificationFailure(Throwable previous, Throwable failure) {
        if (previous == null) return failure;
        if (previous != failure) previous.addSuppressed(failure);
        return previous;
    }

    private static void discardPreparedWorldDrops(List<EntityItem.PreparedDrop> tickets) {
        Throwable failure = null;
        for (EntityItem.PreparedDrop ticket : tickets) {
            try { ticket.discard(); } catch (RuntimeException | Error cleanup) {
                failure = collectWorldNotificationFailure(failure, cleanup);
            }
        }
        if (failure instanceof RuntimeException error) throw error;
        if (failure instanceof Error error) throw error;
    }

    private boolean rejectPreparedWorldDrop(List<EntityItem.PreparedDrop> tickets, Map<BaseInventory, Map<Integer, Item>> before,
                                            InventoryMutationGuard.NativeMetadataSnapshot metadata,
                                            List<InventoryAction> actions, Set<Inventory> inventories) {
        try { discardPreparedWorldDrops(tickets); } finally { metadata.restore(); restoreSlotTransfer(before); }
        this.resyncWorldInventories(actions, inventories);
        return false;
    }

    private void resyncWorldInventories(List<InventoryAction> actions, Set<Inventory> inventories) {
        Throwable failure = null;
        try { this.sendInventories(actions, inventories); } catch (RuntimeException | Error update) { failure = update; }
        for (Inventory inventory : inventories) {
            try { inventory.sendContents(this.source); } catch (RuntimeException | Error update) {
                failure = collectWorldNotificationFailure(failure, update);
            }
        }
        if (failure instanceof RuntimeException error) throw error;
        if (failure instanceof Error error) throw error;
    }

    private static Map<BaseInventory, Map<Integer, Item>> expectedWorldBackings(
            Map<BaseInventory, Map<Integer, Item>> before, List<SlotChangeAction> writes,
            Map<InventoryAction, WorldActionSnapshot> snapshots) {
        Map<BaseInventory, Map<Integer, Item>> expected = new IdentityHashMap<>();
        before.forEach((inventory, slots) -> {
            Map<Integer, Item> copy = new HashMap<>();
            slots.forEach((slot, item) -> copy.put(slot, item.clone()));
            expected.put(inventory, copy);
        });
        for (SlotChangeAction action : writes) {
            BaseInventory backing = (BaseInventory) action.getInventory();
            int slot = action.getSlot();
            if (backing instanceof DoubleChestInventory chest) {
                int leftSize = chest.getLeftSide().getSize();
                backing = slot < leftSize ? chest.getLeftSide() : chest.getRightSide();
                if (slot >= leftSize) slot -= leftSize;
            } else if (backing instanceof PlayerUIComponent component) {
                slot = component.getBackingSlot(slot);
                backing = component.getBackingInventory();
            }
            Item target = snapshots.get(action).target();
            if (target.isNull()) expected.get(backing).remove(slot);
            else expected.get(backing).put(slot, target.clone());
        }
        return expected;
    }

    private static boolean matchesWorldBackings(Map<BaseInventory, Map<Integer, Item>> expected) {
        for (var entry : expected.entrySet()) {
            Map<Integer, Item> actual = entry.getKey().slots;
            if (actual.size() != entry.getValue().size()) return false;
            for (var slot : entry.getValue().entrySet()) {
                Item current = actual.get(slot.getKey());
                if (current == null || !InventoryMutationGuard.matchesTarget(current, slot.getValue())) return false;
            }
        }
        return true;
    }

    private boolean worldActionsIntact(List<InventoryAction> frozenActions, Set<Inventory> frozenInventories,
                                       Map<InventoryAction, WorldActionSnapshot> snapshots) {
        if (!matchesCreativeMembership(frozenActions, frozenInventories)) return false;
        for (InventoryAction action : frozenActions) {
            WorldActionSnapshot expected = snapshots.get(action);
            if (!action.getSourceItem().equalsExact(expected.source())
                    || !action.getTargetItem().equalsExact(expected.target())) return false;
        }
        return matchesCreativeMembership(frozenActions, frozenInventories);
    }

    private boolean worldActorIntact(WorldActorSnapshot context) {
        Player actor = context.actor();
        return this.source == actor && !actor.closed && actor.spawned && actor.isAlive() && actor.isOnline()
                && !actor.isSpectator() && actor.getLevel() == context.level() && actor.getGameVersion() == context.version()
                && actor.protocol == context.protocol() && actor.getGamemode() == context.mode()
                && actor.x == context.x() && actor.y == context.y() && actor.z == context.z()
                && actor.yaw == context.yaw() && actor.pitch == context.pitch();
    }

    private record WorldActorSnapshot(Player actor, cn.nukkit.level.Level level, cn.nukkit.GameVersion version,
                                      int protocol, int mode, double x, double y, double z, double yaw, double pitch) {
        private WorldActorSnapshot(Player actor) {
            this(actor, actor.getLevel(), actor.getGameVersion(), actor.protocol, actor.getGamemode(),
                    actor.x, actor.y, actor.z, actor.yaw, actor.pitch);
        }
    }

    private record WorldActionSnapshot(Item source, Item target) { }

    @SuppressWarnings("try") // Expected-write scopes enforce setters through the thread-local guard.
    private boolean executeSlotTransfer() {
        List<InventoryAction> frozenActions = List.copyOf(this.actions);
        Set<Inventory> frozenInventories = Collections.newSetFromMap(new IdentityHashMap<>());
        frozenInventories.addAll(this.inventories);
        try (InventoryMutationGuard.Lease claim = InventoryMutationGuard.tryClaim(frozenInventories)) {
            if (claim == null) return false;
            Map<CreativeInventoryAction, CreativeActionSnapshot> virtual = new IdentityHashMap<>();
            for (InventoryAction action : frozenActions) {
                if (action.getClass() == CreativeInventoryAction.class) {
                    CreativeInventoryAction creative = (CreativeInventoryAction) action;
                    if (!creative.isValid(this.source)) {
                        this.sendInventories(frozenActions, frozenInventories);
                        return false;
                    }
                    virtual.put(creative, new CreativeActionSnapshot(creative.getActionType(),
                            creative.getSourceItem(), creative.getTargetItem()));
                }
            }
            if (this.hasExecuted() || !matchItems(false, false, frozenActions) || this.invalid
                    || !matchesCreativeTransfer(frozenActions, frozenInventories, virtual)) {
                this.sendInventories(frozenActions, frozenInventories);
                return false;
            }
            if (!(virtual.isEmpty() ? this.callExecuteEvent() : callExecuteEvents(this, frozenActions))) {
                this.sendInventories(frozenActions, frozenInventories);
                return true;
            }
            if (!matchesCreativeTransfer(frozenActions, frozenInventories, virtual)) {
                this.sendInventories(frozenActions, frozenInventories);
                return false;
            }
            List<InventoryAction> preparedActions = virtual.isEmpty() ? List.copyOf(this.actions) : frozenActions;
            for (InventoryAction action : preparedActions) {
                if ((action.getClass() != SlotChangeAction.class && action.getClass() != CreativeInventoryAction.class)
                        || action.getClass() == CreativeInventoryAction.class && !virtual.containsKey(action)
                        || action instanceof SlotChangeAction slot && !claim.contains(slot.getInventory())
                        || !action.onPreExecute(this.source)) {
                    this.sendInventories(frozenActions, frozenInventories);
                    return false;
                }
            }
            for (InventoryAction action : preparedActions) {
                if (action instanceof SlotChangeAction change && !this.source.isCreative()
                        && change.getInventory() == this.source.getInventory()
                        && change.getSlot() >= 36 && change.getSlot() <= 39
                        && action.getSourceItem().hasEnchantment(Enchantment.ID_BINDING_CURSE)) {
                    this.sendInventories(frozenActions, frozenInventories);
                    return false;
                }
            }
            // A transaction callback may have changed either slot or its authored action.
            if (this.actions.isEmpty() || !matchesCreativeTransfer(frozenActions, frozenInventories, virtual)
                    || !matchItems(false, false, List.copyOf(this.actions)) || this.invalid
                    || !matchesCreativeTransfer(frozenActions, frozenInventories, virtual)) {
                this.sendInventories(frozenActions, frozenInventories);
                return false;
            }
            List<SlotChangeAction> writes = this.actions.stream().filter(action -> action.getClass() == SlotChangeAction.class)
                    .map(action -> (SlotChangeAction) action).toList();
            // A valid item stack can still exceed a container's smaller configured limit.
            // Reject before any setter can route excess into the world and defeat rollback.
            for (SlotChangeAction action : writes) {
                Item target = action.getTargetItem();
                if (!target.isNull() && (target.getCount() <= 0 || target.getCount()
                        > Math.min(action.getInventory().getMaxStackSize(), target.getMaxStackSize()))) {
                    this.sendInventories(frozenActions, frozenInventories);
                    return false;
                }
            }
            Map<BaseInventory, Map<Integer, Item>> before = new IdentityHashMap<>();
            Map<SlotChangeAction, Item> expected = new IdentityHashMap<>();
            writes.forEach(action -> expected.put(action, action.getTargetItem()));
            for (SlotChangeAction action : writes) {
                captureSlotTransfer((BaseInventory) action.getInventory(), before);
            }
            try {
                for (SlotChangeAction action : writes) {
                    try (InventoryMutationGuard.WriteScope write = claim.expectWrite(action.getInventory(), action.getSlot(), expected.get(action))) {
                        if (!action.executeInventoryWrite(this.source)
                                || !acceptedSlotTarget(action, expected.get(action))) {
                            restoreSlotTransfer(before);
                            this.sendInventories(frozenActions, frozenInventories);
                            return false;
                        }
                    }
                }
                // A later callback must not rewrite an earlier accepted half of the transfer.
                for (SlotChangeAction action : writes) {
                    if (!acceptedSlotTarget(action, expected.get(action))) {
                        restoreSlotTransfer(before);
                        this.sendInventories(frozenActions, frozenInventories);
                        return false;
                    }
                }
                // Recheck after the last native callback/read, before any irreversible committed effects.
                if (!matchesCreativeTransfer(frozenActions, frozenInventories, virtual)) {
                    restoreSlotTransfer(before);
                    this.sendInventories(frozenActions, frozenInventories);
                    return false;
                }
            } catch (RuntimeException | Error failure) {
                restoreSlotTransfer(before);
                try {
                    this.sendInventories(frozenActions, frozenInventories);
                } catch (RuntimeException | Error updateFailure) {
                    if (updateFailure != failure) failure.addSuppressed(updateFailure);
                }
                throw failure;
            }
            this.hasExecuted = true;
            for (SlotChangeAction action : writes) {
                action.onInventoryWriteCommitted(this.source);
                action.onExecuteSuccess(this.source);
            }
            return true;
        }
    }

    private boolean matchesCreativeTransfer(List<InventoryAction> frozenActions, Set<Inventory> frozenInventories,
                                            Map<CreativeInventoryAction, CreativeActionSnapshot> virtual) {
        if (virtual.isEmpty()) return true;
        if (!matchesCreativeMembership(frozenActions, frozenInventories)) return false;
        for (var entry : virtual.entrySet()) {
            CreativeInventoryAction action = entry.getKey();
            CreativeActionSnapshot snapshot = entry.getValue();
            if (!action.isValid(this.source) || action.getActionType() != snapshot.type()
                    || !action.getSourceItem().equalsExact(snapshot.source())
                    || !action.getTargetItem().equalsExact(snapshot.target())) return false;
        }
        return matchesCreativeMembership(frozenActions, frozenInventories);
    }

    private boolean matchesCreativeMembership(List<InventoryAction> frozenActions, Set<Inventory> frozenInventories) {
        if (this.actions.size() != frozenActions.size() || this.inventories.size() != frozenInventories.size()
                || !frozenInventories.containsAll(this.inventories)) return false;
        for (int i = 0; i < frozenActions.size(); i++) {
            if (this.actions.get(i) != frozenActions.get(i)) return false;
        }
        return true;
    }

    private record CreativeActionSnapshot(int type, Item source, Item target) { }

    private static boolean acceptedSlotTarget(SlotChangeAction action, Item expected) {
        return InventoryMutationGuard.matchesTarget(action.getInventory().getItem(action.getSlot()), expected);
    }

    private static void captureSlotTransfer(BaseInventory inventory, Map<BaseInventory, Map<Integer, Item>> before) {
        if (inventory instanceof DoubleChestInventory chest) {
            captureSlotTransfer(chest.getLeftSide(), before);
            captureSlotTransfer(chest.getRightSide(), before);
        } else if (inventory instanceof PlayerUIComponent component) {
            captureSlotTransfer(component.getBackingInventory(), before);
        } else {
            before.computeIfAbsent(inventory, key -> {
                Map<Integer, Item> snapshot = new HashMap<>();
                key.slots.forEach((slot, stack) -> snapshot.put(slot, stack.clone()));
                return snapshot;
            });
        }
    }

    private static void restoreSlotTransfer(Map<BaseInventory, Map<Integer, Item>> before) {
        // Restoring a snapshot is not another grant: bypass cancellable setters and overflow routing.
        before.forEach((inventory, snapshot) -> {
            inventory.slots.clear();
            inventory.slots.putAll(snapshot);
        });
        // Bundle slots are mirrored into their holder's serialized item NBT.
        before.keySet().forEach(inventory -> {
            if (inventory instanceof BundleInventory bundle) bundle.getHolder().saveNBT();
        });
    }

    public boolean hasExecuted() {
        return this.hasExecuted;
    }

    public boolean checkForItemPart(List<InventoryAction> actions) {
        return false;
    }
}
