package dev.laakirun.veyloria.server.game;

import java.util.ArrayList;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public final class PartyService {
    public static final int MAX_MEMBERS = 5;

    private final Map<UUID, UUID> partyByMember = new ConcurrentHashMap<>();
    private final Map<UUID, Set<UUID>> membersByParty = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> leaderByParty = new ConcurrentHashMap<>();

    public synchronized PartyUpdateResult addMember(UUID requester, UUID target) {
        if (requester == null || target == null) {
            return new PartyUpdateResult(Status.INVALID_REQUEST, null, 0, null);
        }
        if (requester.equals(target)) {
            return new PartyUpdateResult(Status.SELF_TARGET, partyByMember.get(requester), sizeUnsafe(requester), leaderUnsafe(requester));
        }
        UUID requesterParty = partyByMember.get(requester);
        UUID targetParty = partyByMember.get(target);

        if (requesterParty == null && targetParty == null) {
            UUID partyId = UUID.randomUUID();
            Set<UUID> members = ConcurrentHashMap.newKeySet();
            members.add(requester);
            members.add(target);
            membersByParty.put(partyId, members);
            partyByMember.put(requester, partyId);
            partyByMember.put(target, partyId);
            leaderByParty.put(partyId, requester);
            return new PartyUpdateResult(Status.CREATED, partyId, members.size(), requester);
        }

        if (requesterParty == null) {
            return new PartyUpdateResult(Status.TARGET_IN_OTHER_PARTY, null, 0, null);
        }
        if (!isLeader(requester)) {
            return new PartyUpdateResult(Status.NOT_LEADER, requesterParty, sizeUnsafe(requester), leaderUnsafe(requester));
        }
        if (targetParty != null) {
            if (targetParty.equals(requesterParty)) {
                return new PartyUpdateResult(Status.TARGET_ALREADY_IN_PARTY, requesterParty, sizeUnsafe(requester), leaderUnsafe(requester));
            }
            return new PartyUpdateResult(Status.TARGET_IN_OTHER_PARTY, requesterParty, sizeUnsafe(requester), leaderUnsafe(requester));
        }
        Set<UUID> members = membersByParty.computeIfAbsent(requesterParty, ignored -> ConcurrentHashMap.newKeySet());
        members.add(requester);
        if (members.size() >= MAX_MEMBERS) {
            return new PartyUpdateResult(Status.PARTY_FULL, requesterParty, members.size(), leaderUnsafe(requester));
        }
        members.add(target);
        partyByMember.put(target, requesterParty);
        return new PartyUpdateResult(Status.ADDED, requesterParty, members.size(), leaderUnsafe(requester));
    }

    public synchronized PartyUpdateResult leave(UUID member) {
        UUID partyId = partyByMember.get(member);
        if (partyId == null) {
            return new PartyUpdateResult(Status.NOT_IN_PARTY, null, 0, null);
        }
        Set<UUID> members = membersByParty.get(partyId);
        if (members == null) {
            partyByMember.remove(member);
            leaderByParty.remove(partyId);
            return new PartyUpdateResult(Status.LEFT, null, 0, null);
        }
        members.remove(member);
        partyByMember.remove(member);
        if (members.isEmpty()) {
            membersByParty.remove(partyId);
            leaderByParty.remove(partyId);
            return new PartyUpdateResult(Status.LEFT, null, 0, null);
        }
        UUID currentLeader = leaderByParty.get(partyId);
        if (member.equals(currentLeader)) {
            leaderByParty.put(partyId, randomMember(members));
        }
        return new PartyUpdateResult(Status.LEFT, partyId, members.size(), leaderByParty.get(partyId));
    }

    public synchronized PartyUpdateResult kick(UUID requester, UUID target) {
        if (requester == null || target == null) {
            return new PartyUpdateResult(Status.INVALID_REQUEST, null, 0, null);
        }
        if (requester.equals(target)) {
            return new PartyUpdateResult(Status.CANNOT_KICK_SELF, partyByMember.get(requester), sizeUnsafe(requester), leaderUnsafe(requester));
        }
        UUID requesterParty = partyByMember.get(requester);
        UUID targetParty = partyByMember.get(target);
        if (requesterParty == null) {
            return new PartyUpdateResult(Status.NOT_IN_PARTY, null, 0, null);
        }
        if (!isLeader(requester)) {
            return new PartyUpdateResult(Status.NOT_LEADER, requesterParty, sizeUnsafe(requester), leaderUnsafe(requester));
        }
        if (!requesterParty.equals(targetParty)) {
            return new PartyUpdateResult(Status.TARGET_NOT_IN_PARTY, requesterParty, sizeUnsafe(requester), leaderUnsafe(requester));
        }
        Set<UUID> members = membersByParty.get(requesterParty);
        if (members == null || !members.remove(target)) {
            return new PartyUpdateResult(Status.TARGET_NOT_IN_PARTY, requesterParty, sizeUnsafe(requester), leaderUnsafe(requester));
        }
        partyByMember.remove(target);
        if (members.isEmpty()) {
            membersByParty.remove(requesterParty);
            leaderByParty.remove(requesterParty);
            return new PartyUpdateResult(Status.KICKED, null, 0, null);
        }
        return new PartyUpdateResult(Status.KICKED, requesterParty, members.size(), leaderByParty.get(requesterParty));
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

    public synchronized boolean isLeader(UUID member) {
        UUID partyId = partyByMember.get(member);
        if (partyId == null) {
            return false;
        }
        return member.equals(leaderByParty.get(partyId));
    }

    public synchronized UUID leaderOf(UUID member) {
        UUID partyId = partyByMember.get(member);
        if (partyId == null) {
            return null;
        }
        return leaderByParty.get(partyId);
    }

    public synchronized int sizeOf(UUID member) {
        return sizeUnsafe(member);
    }

    public synchronized UUID partyIdOf(UUID member) {
        return partyByMember.get(member);
    }

    public synchronized void removeMember(UUID member) {
        leave(member);
    }

    private int sizeUnsafe(UUID member) {
        UUID partyId = partyByMember.get(member);
        if (partyId == null) {
            return 1;
        }
        return membersByParty.getOrDefault(partyId, Set.of(member)).size();
    }

    private UUID leaderUnsafe(UUID member) {
        UUID partyId = partyByMember.get(member);
        if (partyId == null) {
            return null;
        }
        return leaderByParty.get(partyId);
    }

    private UUID randomMember(Set<UUID> members) {
        if (members.isEmpty()) {
            return null;
        }
        int index = ThreadLocalRandom.current().nextInt(members.size());
        int current = 0;
        for (UUID member : members) {
            if (current == index) {
                return member;
            }
            current++;
        }
        return new ArrayList<>(members).get(0);
    }

    public enum Status {
        CREATED,
        ADDED,
        LEFT,
        KICKED,
        NOT_IN_PARTY,
        NOT_LEADER,
        PARTY_FULL,
        TARGET_IN_OTHER_PARTY,
        TARGET_ALREADY_IN_PARTY,
        TARGET_NOT_IN_PARTY,
        SELF_TARGET,
        CANNOT_KICK_SELF,
        INVALID_REQUEST
    }

    public record PartyUpdateResult(Status status, UUID partyId, int memberCount, UUID leaderUuid) {
        public boolean success() {
            return status == Status.CREATED || status == Status.ADDED || status == Status.LEFT || status == Status.KICKED;
        }
    }
}
