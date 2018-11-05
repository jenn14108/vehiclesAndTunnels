package cs131.pa2.CarsTunnels;

import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map.Entry;
import java.util.PriorityQueue;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import cs131.pa2.Abstract.Tunnel;
import cs131.pa2.Abstract.Vehicle;
import cs131.pa2.Abstract.Log.Log;

public class PreemptivePriorityScheduler extends Tunnel{
	
	private Lock schedulerLock;
	private Condition priorityMet;
	private Condition ambulCanEnter;
	
	private Collection<Tunnel> tunnels;
	private HashMap<Vehicle,Tunnel> vehiclesToTunnels;
	private PriorityQueue<Vehicle> carPriority;
	private HashMap<Tunnel,Lock> tunnelLocks;
	private HashMap<Lock,Condition> tunLockCond; 

	public PreemptivePriorityScheduler(String name, Collection<Tunnel> tunnels, Log log) {
		super(name,log);
		this.tunnels = tunnels;
		
		this.schedulerLock = new ReentrantLock(); 
		this.priorityMet = this.schedulerLock.newCondition();
		this.ambulCanEnter = this.schedulerLock.newCondition(); 
		
		this.vehiclesToTunnels = new HashMap<>();
		this.tunnelLocks = new HashMap<>();
		this.tunLockCond = new HashMap<>();
		
		for (Tunnel t: tunnels) {
			Lock tunnelLock =  new ReentrantLock(); 
			tunnelLocks.put(t, tunnelLock);
			Condition tunnelForAmbul = tunnelLock.newCondition();
			this.tunLockCond.put(tunnelLock, tunnelForAmbul);
		}
		this.carPriority = new PriorityQueue<>(new Comparator<Vehicle>() {
			@Override
			public int compare(Vehicle a, Vehicle b) {
				return b.getPriority() - a.getPriority();
			}
		});
	}

	@Override
	public boolean tryToEnterInner(Vehicle vehicle) {
		this.schedulerLock.lock();
		BasicTunnel freeTunnel = null;
		try {
			if (vehicle instanceof Ambulance) {
				while (freeTunnel == null) {
					freeTunnel = (BasicTunnel)this.checkForFreeTunnel(vehicle);
					if (freeTunnel == null) {
						ambulCanEnter.await();
					}
				}
				Lock tunnelLock = this.tunnelLocks.get(freeTunnel);
				vehicle.setLock(tunnelLock, this.tunLockCond.get(tunnelLock));
				
				
				vehiclesToTunnels.put(vehicle, freeTunnel);
				return true;
			} else {
				carPriority.add(vehicle);
				while (!carPriority.peek().equals(vehicle)) {
					this.priorityMet.await();
				}
				carPriority.remove(vehicle);
				priorityMet.signalAll();
				
				while (freeTunnel == null) {
					freeTunnel = (BasicTunnel)this.checkForFreeTunnel(vehicle);
				}
				Lock tunnelLock = this.tunnelLocks.get(freeTunnel);
				
				vehicle.setLock(tunnelLock, this.tunLockCond.get(tunnelLock));

				vehiclesToTunnels.put(vehicle, freeTunnel);
				return true;
			}
		} catch (InterruptedException e) {
			e.printStackTrace();
		} finally {
			this.schedulerLock.unlock();
		}
		return false;
	}

	@Override
	public void exitTunnelInner(Vehicle vehicle) {
		this.schedulerLock.lock();
		try {
			BasicTunnel tunnel = (BasicTunnel) vehiclesToTunnels.get(vehicle);
			if (vehicle instanceof Ambulance) {
				tunnel.exitTunnelInner(vehicle);
				this.ambulCanEnter.signalAll();
				this.vehiclesToTunnels.remove(vehicle);
			} else {
				tunnel.exitTunnelInner(vehicle);
				this.vehiclesToTunnels.remove(vehicle);
			}
		} finally {
			this.schedulerLock.unlock();
		}
	}
	
	public Tunnel checkForFreeTunnel(Vehicle vehicle) {
		for (Tunnel t : tunnels) {
			if (t.tryToEnter(vehicle) == true) {
				return t;
			}
		}
		return null;
	}
}

