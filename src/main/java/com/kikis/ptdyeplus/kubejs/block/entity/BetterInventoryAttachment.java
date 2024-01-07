package com.kikis.ptdyeplus.kubejs.block.entity;

import dev.latvian.mods.kubejs.block.entity.BlockEntityAttachment;
import dev.latvian.mods.kubejs.block.entity.BlockEntityAttachmentType;
import dev.latvian.mods.kubejs.block.entity.BlockEntityJS;
import dev.latvian.mods.kubejs.core.InventoryKJS;
import dev.latvian.mods.kubejs.item.ingredient.IngredientJS;
import dev.latvian.mods.kubejs.typings.desc.PrimitiveDescJS;
import dev.latvian.mods.kubejs.typings.desc.TypeDescJS;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityProvider;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.ItemStackHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@SuppressWarnings("UnstableApiUsage")
public class BetterInventoryAttachment extends CapabilityProvider<BetterInventoryAttachment> implements BlockEntityAttachment, InventoryKJS {
    public static final BlockEntityAttachmentType TYPE = new BlockEntityAttachmentType(
            "better inventory",
            TypeDescJS.object()
                    .add("width", TypeDescJS.NUMBER, false)
                    .add("height", TypeDescJS.NUMBER, false)
                    .add("inputFilter", new PrimitiveDescJS("Ingredient"), true),
            map -> {
                var width = ((Number) map.get("width")).intValue();
                var height = ((Number) map.get("height")).intValue();
                var inputFilter = map.containsKey("inputFilter") ? IngredientJS.of(map.get("inputFilter")) : null;
                return entity -> new BetterInventoryAttachment(entity, width, height, inputFilter);
            }
    );

    public final int width, height;
    public final BlockEntityJS blockEntity;
    public final Ingredient inputFilter;
    private final ItemStackHandler inventory;
    private final LazyOptional<ItemStackHandler> optional;
    private final NonNullList<ItemStack> items;

    public BetterInventoryAttachment(BlockEntityJS blockEntity, int width, int height, @Nullable Ingredient inputFilter) {
        super(BetterInventoryAttachment.class);
        this.blockEntity = blockEntity;
        this.width = width;
        this.height = height;
        this.inputFilter = inputFilter;

        this.inventory = new ItemStackHandler(width * height){
            @Override
            protected void onContentsChanged(int slot) {
                super.onContentsChanged(slot);
                BetterInventoryAttachment.this.setChanged();
            }
        };
        this.optional = LazyOptional.of(() -> this.inventory);
        this.items = NonNullList.withSize(width * height, ItemStack.EMPTY);
    }

    public void setChanged() {
        this.blockEntity.save();
    }

    public int getContainerSize() {
        return this.width * this.height;
    }

    public int getMaxStackSize() {
        return 64;
    }

    public ItemStack getItem(int slot) {
        return slot >= 0 && slot < this.items.size() ? this.items.get(slot) : ItemStack.EMPTY;
    }

    private ItemStack insertItem(int slot, ItemStack itemStack) {
        ItemStack itemSlot = this.getItem(slot);
        int maxStackSize = this.getMaxStackSize();

        if (itemSlot.isEmpty()) {
            if (itemStack.getCount() <= maxStackSize) {
                this.setItem(slot, itemStack);
                return ItemStack.EMPTY;
            }
            ItemStack copy = itemStack.copy();
            copy.setCount(maxStackSize);
            this.setItem(slot, copy);
            ItemStack returnStack = itemStack.copy();
            returnStack.setCount(returnStack.getCount()-maxStackSize);
            return returnStack;
        }
        else if (!ItemStack.isSameItemSameTags(itemSlot, itemStack))
            return itemStack;

        int taken = maxStackSize - itemSlot.getCount();
        itemSlot.setCount(maxStackSize);
        ItemStack returnStack = itemStack.copy();
        returnStack.setCount(returnStack.getCount()-taken);
        return returnStack;
    }

    public boolean canAddItem(ItemStack itemStack) {
        if (!(this.inputFilter == null || this.inputFilter.test(itemStack)))
            return false;

        for (ItemStack itemSlot : this.items) {
            if (itemSlot.isEmpty() || ItemStack.isSameItemSameTags(itemSlot, itemStack) && itemSlot.getCount() < itemSlot.getMaxStackSize()) {
                return true;
            }
        }
        return false;
    }

    @SuppressWarnings("unused")
    public boolean canPlaceItem(int slot, ItemStack itemStack) {
        //return (this.inputFilter == null || this.inputFilter.test(itemStack));
        return true;
    }

    public ItemStack removeItemNoUpdate(int slot) {
        ItemStack itemStack = this.items.get(slot);
        if (itemStack.isEmpty()) {
            return ItemStack.EMPTY;
        }
        this.items.set(slot, ItemStack.EMPTY);
        return itemStack;
    }

    public ItemStack removeItem(int slot, int amount) {
        if (slot >= 0 && slot < this.items.size() &&
            !this.items.get(slot).isEmpty() && amount > 0) {
            return this.items.get(slot).split(amount);
        }
        this.setChanged();
        return ItemStack.EMPTY;
    }

    public void setItem(int slot, ItemStack itemStack) {
        this.items.set(slot, itemStack);
        if (!itemStack.isEmpty() && itemStack.getCount() > this.getMaxStackSize())
            itemStack.setCount(this.getMaxStackSize());
        this.setChanged();
    }

    public CompoundTag writeAttachment() {
        CompoundTag tag = new CompoundTag();
        ListTag list = new ListTag();

        for(int i = 0; i < this.getContainerSize(); ++i) {
            ItemStack stack = this.getItem(i);
            if (!stack.isEmpty()) {
                CompoundTag itemTag = new CompoundTag();
                itemTag.putByte("Slot", (byte)i);
                stack.save(itemTag);
                list.add(itemTag);
            }
        }

        tag.put("items", list);
        return tag;
    }

    @Override
    public void readAttachment(CompoundTag tag) {
        for(int i = 0; i < getContainerSize(); ++i) {
            this.removeItemNoUpdate(i);
        }

        ListTag list = tag.getList("items", 10);

        for(int i = 0; i < list.size(); ++i) {
            CompoundTag itemTag = list.getCompound(i);
            byte slot = itemTag.getByte("Slot");
            if (slot >= 0 && slot < getContainerSize()) {
                this.setItem(slot, ItemStack.of(itemTag));
            }
        }
    }

    @SuppressWarnings("DataFlowIssue")
    @Override
    public void onRemove(BlockState newState) {
        Containers.dropContents(this.blockEntity.getLevel(), this.blockEntity.getBlockPos(), this.items);
    }

    @Override
    public @NotNull <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap) {
        if (cap == ForgeCapabilities.ITEM_HANDLER) {
            return this.optional.cast();
        }
        return super.getCapability(cap);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        this.optional.invalidate();
    }

    @Override
    public int kjs$getWidth() {
        return this.width;
    }

    @Override
    public int kjs$getHeight() {
        return this.height;
    }

    @Override
    public int kjs$getSlots() {
        return this.getContainerSize();
    }

    @Override
    public ItemStack kjs$getStackInSlot(int slot) {
        return this.getItem(slot);
    }

    @Override
    public void kjs$setStackInSlot(int slot, ItemStack stack) {
        this.setItem(slot, stack);
    }

    @Override
    public void kjs$setChanged() {
        this.setChanged();
    }

    public boolean kjs$isItemValid(int slot, @NotNull ItemStack stack) {
        return this.canPlaceItem(slot, stack);
    }

    @Override
    public int kjs$getSlotLimit(int slot) {
        ItemStack itemStack = this.getItem(slot);
        return itemStack.isEmpty() ? this.getMaxStackSize() : itemStack.getMaxStackSize();
    }

    @Override
    public ItemStack kjs$insertItem(int slot, ItemStack stack, boolean simulate) {
        return this.insertItem(slot, stack);
    }

    @Override
    public ItemStack kjs$extractItem(int slot, int amount, boolean simulate) {
        if (amount == 0) {
            return ItemStack.EMPTY;
        }
        ItemStack itemSlot = this.getItem(slot);
        if (itemSlot.isEmpty())
            return ItemStack.EMPTY;
        if (simulate) {
            if (itemSlot.getCount() < amount)
                return itemSlot.copy();
            ItemStack copy = itemSlot.copy();
            copy.setCount(amount);
            return copy;
        }
        int m = Math.min(itemSlot.getCount(), amount);
        ItemStack returnStack = this.removeItem(slot, m);
        this.setChanged();
        return returnStack;
    }
}