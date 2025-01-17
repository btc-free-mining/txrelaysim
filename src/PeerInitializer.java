package txrelaysim.src;

import txrelaysim.src.helpers.*;

import java.util.HashSet;
import java.util.HashMap;

import peersim.config.*;
import peersim.core.*;
import peersim.edsim.EDSimulator;
import peersim.transport.Transport;

public class PeerInitializer implements Control
{
	private int pid;
	private int reachableCount;
	private int privateBlackHolesPercent;
	private int outPeersLegacy;
	private int outPeersRecon;
	private int inRelayDelayReconPeer;
	private int outRelayDelayReconPeer;
	private int inRelayDelayLegacyPeer;
	private int outRelayDelayLegacyPeer;

	// Reconciliation params
	private int reconcilePercent;
	private double outFloodPeersPercent;
	private double inFloodPeersPercent;
	private double defaultQ;
	private int reconciliationInterval;

	public PeerInitializer(String prefix) {
		pid = Configuration.getPid(prefix + "." + "protocol");
		reachableCount = Configuration.getInt(prefix + "." + "reachable_count");
		outPeersLegacy = Configuration.getInt(prefix + "." + "out_peers_legacy");
		outPeersRecon = Configuration.getInt(prefix + "." + "out_peers_recon");
		inRelayDelayReconPeer = Configuration.getInt(prefix + "." + "in_relay_delay_recon_peer");
		outRelayDelayReconPeer = Configuration.getInt(prefix + "." + "out_relay_delay_recon_peer");
		inRelayDelayLegacyPeer = Configuration.getInt(prefix + "." + "in_relay_delay_legacy_peer");
		outRelayDelayLegacyPeer = Configuration.getInt(prefix + "." + "out_relay_delay_legacy_peer");
		privateBlackHolesPercent = Configuration.getInt(prefix + "." + "private_black_holes_percent", 0);
		reconcilePercent = Configuration.getInt(prefix + "." + "reconcile_percent");
		if (reconcilePercent > 0) {
			reconciliationInterval = Configuration.getInt(prefix + "." + "reconciliation_interval");
			defaultQ = Configuration.getDouble(prefix + "." + "default_q");
			outFloodPeersPercent = Configuration.getDouble(prefix + "." + "out_flood_peers_percent");
			inFloodPeersPercent = Configuration.getDouble(prefix + "." + "in_flood_peers_percent");
		}
	}

	@Override
	public boolean execute() {
		Peer.pidPeer = pid;

		int privateBlackHolesCount = (Network.size() - reachableCount) * privateBlackHolesPercent / 100;
		// Set a subset of nodes to be reachable by other nodes.
		while (reachableCount > 0) {
			int r = CommonState.r.nextInt(Network.size() - 1) + 1;
			if (!((Peer)Network.get(r).getProtocol(pid)).isReachable) {
				((Peer)Network.get(r).getProtocol(pid)).isReachable = true;
				--reachableCount;
			}
		}

		System.err.println("Black holes: " + privateBlackHolesCount);
		while (privateBlackHolesCount > 0) {
			int r = CommonState.r.nextInt(Network.size() - 1) + 1;
			if (!((Peer)Network.get(r).getProtocol(pid)).isReachable) {
				((Peer)Network.get(r).getProtocol(pid)).isBlackHole = true;
				--privateBlackHolesCount;
			}
		}
		System.err.println("Black holes: " + privateBlackHolesCount);

		int reconcilingNodes = Network.size() * reconcilePercent / 100;
		// A list storing who is already connected to who, so that we don't make duplicate conns.
		HashMap<Integer, HashSet<Integer>> peers = new HashMap<>();
		for (int i = 1; i < Network.size(); i++) {
			peers.put(i, new HashSet<>());
			// Initial parameters setting for all nodes.

			if (reconcilingNodes > 0) {
				reconcilingNodes--;
				((Peer)Network.get(i).getProtocol(pid)).reconcile = true;
				((Peer)Network.get(i).getProtocol(pid)).reconciliationInterval = reconciliationInterval;
				((Peer)Network.get(i).getProtocol(pid)).inFloodLimitPercent = inFloodPeersPercent;
				((Peer)Network.get(i).getProtocol(pid)).outFloodLimitPercent = outFloodPeersPercent;
				((Peer)Network.get(i).getProtocol(pid)).reconciliationInterval = reconciliationInterval;
				((Peer)Network.get(i).getProtocol(pid)).defaultQ = defaultQ;
				((Peer)Network.get(i).getProtocol(pid)).inRelayDelay = inRelayDelayReconPeer;
				((Peer)Network.get(i).getProtocol(pid)).outRelayDelay = outRelayDelayReconPeer;
			} else {
				((Peer)Network.get(i).getProtocol(pid)).reconcile = false;
				((Peer)Network.get(i).getProtocol(pid)).inFloodLimitPercent = 100;
				((Peer)Network.get(i).getProtocol(pid)).outFloodLimitPercent = 100;
				((Peer)Network.get(i).getProtocol(pid)).inRelayDelay = inRelayDelayLegacyPeer;
				((Peer)Network.get(i).getProtocol(pid)).outRelayDelay = outRelayDelayLegacyPeer;
			}
		}

		// Connect all nodes to a limited number of reachable nodes.
		for(int i = 1; i < Network.size(); i++) {
			Node curNode = Network.get(i);
			int connsTarget;
			if (((Peer)curNode.getProtocol(pid)).reconcile) {
				connsTarget = outPeersRecon;
			} else {
				connsTarget = outPeersLegacy;
			}
			while (connsTarget > 0) {
				int randomNodeIndex = CommonState.r.nextInt(Network.size() - 1) + 1;
				if (randomNodeIndex == i) {
					continue;
				}

				Node randomNode = Network.get(randomNodeIndex);
				Peer randomNodeState = ((Peer)Network.get(randomNodeIndex).getProtocol(pid));

				if (!randomNodeState.isReachable) {
					continue;
				}
				if (peers.get(i).contains(randomNodeIndex) || peers.get(randomNodeIndex).contains(i)) {
					continue;
				}

				peers.get(i).add(randomNodeIndex);
				peers.get(randomNodeIndex).add(i);

				// Actual connecting.
				boolean curNodeSupportsRecon = ((Peer)Network.get(i).getProtocol(pid)).reconcile;
				((Peer)curNode.getProtocol(pid)).addPeer(randomNode, true, randomNodeState.reconcile);
				((Peer)randomNode.getProtocol(pid)).addPeer(curNode, false, curNodeSupportsRecon);
				--connsTarget;
			}
		}

		System.err.println("Initialized peers");
		return true;
	}
}