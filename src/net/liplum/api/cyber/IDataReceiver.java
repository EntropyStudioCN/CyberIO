package net.liplum.api.cyber;

import arc.struct.ObjectSet;
import mindustry.type.Item;
import net.liplum.CalledBySync;
import net.liplum.ClientOnly;
import net.liplum.lib.delegates.Delegate1;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface IDataReceiver extends IDataBuilding {
    void receiveData(@NotNull IDataSender sender, @NotNull Item item, int amount);

    /**
     * Gets the max acceptable number of this {@code item}.
     * -1 means any
     *
     * @param sender sender
     * @param itme   item
     * @return amount
     */
    int acceptedAmount(@NotNull IDataSender sender, @NotNull Item itme);

    /**
     * Gets what this receiver wants<br/>
     * null : Any<br/>
     * Array.Empty : Nothing<br/>
     * An array : what's in the array<br/>
     *
     * @return what this receiver wants
     */
    @Nullable
    Item[] getRequirements();

    @ClientOnly
    boolean isBlocked();

    @CalledBySync
    default void connect(@NotNull IDataSender sender) {
        getConnectedSenders().add(sender.getBuilding().pos());
    }

    @CalledBySync
    default void disconnect(@NotNull IDataSender sender) {
        getConnectedSenders().remove(sender.getBuilding().pos());
    }

    @NotNull
    ObjectSet<Integer> getConnectedSenders();

    default boolean isConnectedWith(@NotNull IDataSender sender) {
        return getConnectedSenders().contains(sender.getBuilding().pos());
    }

    @NotNull
    Delegate1<IDataReceiver> getOnRequirementUpdated();

    /**
     * Gets the maximum limit of connection.<br/>
     * -1 : unlimited
     *
     * @return the maximum of connection
     */
    int maxSenderConnection();


    default boolean acceptConnection(@NotNull IDataSender host) {
        return canHaveMoreSenderConnection();
    }

    default int getSenderConnectionNumber() {
        return getConnectedSenders().size;
    }

    default boolean canHaveMoreSenderConnection() {
        int max = maxSenderConnection();
        if (max == -1) {
            return true;
        }
        return getConnectedSenders().size < max;
    }
}
