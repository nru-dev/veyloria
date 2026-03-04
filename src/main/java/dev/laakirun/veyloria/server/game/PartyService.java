package dev.laakirun.veyloria.server.game;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class PartyService {
    private final Map<UUID, UUID> partyByMember = new ConcurrentHashMap<>();
    private final Map<UUID, Set<UUID>> membersByParty = new ConcurrentHashMap<>();

    public synchronized PartyUpdateResult addMember(UUID owner, UUID target) {
        UUID ownerParty = partyByMember.get(owner);
        UUID targetParty = partyByMember.get(target);

        if (ownerParty == null && targetParty == null) {
            UUID partyId = UUID.randomUUID();
            Set<UUID> members = ConcurrentHashMap.newKeySet();
            members.add(owner);
            members.add(target);
            membersByParty.put(partyId, members);
            partyByMember.put(owner, partyId);
            partyByMember.put(target, partyId);
            return new PartyUpdateResult(partyId, members.size(), true, false);
        }

        if (ownerParty != null && ownerParty.equals(targetParty)) {
            Set<UUID> members = membersByParty.getOrDefault(ownerParty, Set.of(owner, target));
            return new PartyUpdateResult(ownerParty, members.size(), false, false);
        }

        if (ownerParty == null) {
            ownerParty = targetParty;
            addToParty(ownerParty, owner);
            return new PartyUpdateResult(ownerParty, membersByParty.getOrDefault(ownerParty, Set.of()).size(), true, false);
        }

        if (targetParty == null) {
            addToParty(ownerParty, target);
            return new PartyUpdateResult(ownerParty, membersByParty.getOrDefault(ownerParty, Set.of()).size(), true, false);
        }

        if (!ownerParty.equals(targetParty)) {
            mergeInto(ownerParty, targetParty);
            return new PartyUpdateResult(ownerParty, membersByParty.getOrDefault(ownerParty, Set.of()).size(), true, true);
        }
        Set<UUID> members = membersByParty.getOrDefault(ownerParty, Set.of());
        return new PartyUpdateResult(ownerParty, members.size(), false, false);
    }

    public synchronized Set<UUID> membersOf(UUID member) {
        UUID partyId = partyByMember.get(member);
        if (partyId == null) {
            return Set.of(member);
        }
        Set<UUID> members = membersByParty.get(partyId);
        if (members == null || members.isEmpty()) {
            return Set.of(member);
        }
        return Set.copyOf(members);
    }

    public synchronized void removeMember(UUID member) {
        UUID partyId = partyByMember.remove(member);
        if (partyId == null) {
            return;
        }
        Set<UUID> members = membersByParty.get(partyId);
        if (members == null) {
            return;
        }
        members.remove(member);
        if (members.size() < 2) {
            for (UUID last : new LinkedHashSet<>(members)) {
                partyByMember.remove(last);
            }
            membersByParty.remove(partyId);
        }
    }

    private void addToParty(UUID partyId, UUID member) {
        if (partyId == null) {
            return;
        }
        membersByParty.computeIfAbsent(partyId, ignored -> ConcurrentHashMap.newKeySet()).add(member);
        partyByMember.put(member, partyId);
    }

    private void mergeInto(UUID destinationParty, UUID sourceParty) {
        Set<UUID> sourceMembers = membersByParty.remove(sourceParty);
        if (sourceMembers == null || sourceMembers.isEmpty()) {
            return;
        }
        Set<UUID> destinationMembers = membersByParty.computeIfAbsent(destinationParty, ignored -> ConcurrentHashMap.newKeySet());
        for (UUID member : sourceMembers) {
            destinationMembers.add(member);
            partyByMember.put(member, destinationParty);
        }
    }

    public record PartyUpdateResult(UUID partyId, int memberCount, boolean changed, boolean merged) {
    }
}
