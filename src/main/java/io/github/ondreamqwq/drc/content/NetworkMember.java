package io.github.ondreamqwq.drc.content;

import java.util.UUID;

/**
 * A block that belongs to one of our networks.
 *
 * <p>This is not Dashpanels' {@code ModulesNetworkMember}: a network here is only a named group of our
 * own blocks, and joining it must never make the game run Dashpanels' split/merge bookkeeping over the
 * panels around it.
 */
public interface NetworkMember {

    UUID networkId();

    void setNetworkId(UUID id);
}
