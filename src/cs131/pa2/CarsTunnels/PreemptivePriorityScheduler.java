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
	private Condition priorityMet; //priority requirement for cars
	private Condition ambulCanEnter; //check for free tunnel for ambulances
	
	private Collection<Tunnel> tunnels; 
	private HashMap<Vehicle,Tunnel> vehiclesToTunnels; //keeps track of each vehicle and its tunnel
	private PriorityQueue<Vehicle> carPriority; //keeps track of cars based on priority
	private HashMap<Tunnel,Lock> tunnelLocks; //keeps track of all tunnels and their respective locks
	private HashMap<Lock,Condition> tunLockCond; //keeps track of the condition on the lock for each tunnel

	/**
	 * The constructor for the preemptivePriorityScheduler
	 */
	public PreemptivePriorityScheduler(String name, Collection<Tunnel> tunnels, Log log) {
		super(name,log);
		this.tunnels = tunnels;
		
		this.schedulerLock = new ReentrantLock(); 
		this.priorityMet = this.schedulerLock.newCondition();
		this.ambulCanEnter = this.schedulerLock.newCondition(); 
		
		this.vehiclesToTunnels = new HashMap<>();
		this.tunnelLocks = new HashMap<>();
		this.tunLockCond = new HashMap<>();
		
		//create a lock and a condition for each tunnel, store in maps
		for (Tunnel t: tunnels) {
			Lock tunnelLock =  new ReentrantLock(); 
			tunnelLocks.put(t, tunnelLock);
			Condition tunnelForAmbul = tunnelLock.newCondition();
			this.tunLockCond.put(tunnelLock, tunnelForAmbul);
		}
		
		//add vehicles to the queue based on priority of vehicles
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
			//ambulances have highest priority, thus only check for free tunnel
			if (vehicle instanceof Ambulance) {
				while (freeTunnel == null) {
					freeTunnel = (BasicTunnel)this.checkForFreeTunnel(vehicle);
					if (freeTunnel == null) {
						ambulCanEnter.await();
					}
				}
				//obtain the lock and condition from that specific tunnel
				//equip the vehicle with that specific tunnel lock 
				Lock tunnelLock = this.tunnelLocks.get(freeTunnel);
				vehicle.setLock(tunnelLock, this.tunLockCond.get(tunnelLock));
			
				vehiclesToTunnels.put(vehicle, freeTunnel);
				return true;
			} else { //other vehicles check priority, then check if there's a free tunnel
				carPriority.add(vehicle);
				
				while (!carPriority.peek().equals(vehicle)) {
					this.priorityMet.await();
				}
				
				while (freeTunnel == null) {
					freeTunnel = (BasicTunnel)this.checkForFreeTunnel(vehicle);
				}
				//obtain lock of the specific tunnel and equip vehicle with that free tunnel's lock 
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
			//obtain the tunnel the vehicle is in 
			BasicTunnel tunnel = (BasicTunnel) vehiclesToTunnels.get(vehicle);
			 
			//if ambulance, exit tunnel and signal other ambulances
			if (vehicle instanceof Ambulance) {
				tunnel.exitTunnelInner(vehicle);
				this.ambulCanEnter.signalAll();
				this.vehiclesToTunnels.remove(vehicle);
			} else {        //normal vehicle, simply exit
				carPriority.remove(vehicle);
				priorityMet.signalAll();
				tunnel.exitTunnelInner(vehicle);
				this.vehiclesToTunnels.remove(vehicle);
			}
			
		} finally {
			this.schedulerLock.unlock();
		}
	}
	
	/**
	 * This method simply checks for free tunnels in the collection of tunnels
	 */
	public Tunnel checkForFreeTunnel(Vehicle vehicle) {
		for (Tunnel t : tunnels) {
			if (t.tryToEnter(vehicle) == true) {
				return t;
			}
		}
		return null;
	}
}

