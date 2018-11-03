package cs131.pa2.CarsTunnels;

import cs131.pa2.Abstract.Tunnel;
import cs131.pa2.Abstract.Vehicle;
import cs131.pa2.Abstract.Direction;
import java.util.*;

public class BasicTunnel extends Tunnel{

	protected Direction tunnelDirection; //needed to keep track of the current direction of the one road in tunnel 
	protected int sleds; //needed to keep track of the number of sleds in the tunnel. Should never exceed 1
	protected int cars; //needed to keep track of the number of cars in the tunnel. Should never exceed 3
	protected int ambulance;
	
	public BasicTunnel(String name) {
		super(name);
		this.sleds = 0;
		this.cars = 0;
		this.ambulance = 0;
		this.tunnelDirection = null; //no direction is determined yet when tunnel is first created
	}

	@Override
	public synchronized boolean tryToEnterInner(Vehicle vehicle) {
		//first check what type of vehicle this is
		//always let ambulance go first
		if (vehicle instanceof Ambulance) {
			if (this.ambulance == 0) {
				this.ambulance ++;
				return true;
			} else { //there is another ambulance 
				return false;
			}
		}
		//vehicle = car, then we need to check that no sled is inside, and the #
		//of cars is < 3
		if (vehicle instanceof Car) {
			if (this.sleds > 0 || this.cars == 3) {
				return false;
			//if there is space in the tunnel, we then check that this specific car is requesting 
			//to go the same direction as others in the tunnel
			} else if (tunnelDirection == null || tunnelDirection.equals(vehicle.getDirection())){
				this.cars ++;
				this.tunnelDirection = vehicle.getDirection();
				return true;
			} else {
				return false;
			}
		} else { //vehicle is an instance of Sled. We check if there is already a sled, or there is a car
			if (this.sleds > 0 || this.cars > 0) {
				return false;
			}  //no need to check direction if Sled tunnel is free, as sled will be the only vehicle inside the tunnel
			this.sleds ++;
			this.tunnelDirection = vehicle.getDirection();
			return true;
		}
	}

	@Override
	public synchronized void exitTunnelInner(Vehicle vehicle) {
		if (vehicle instanceof Ambulance) {
			this.ambulance --;
		}
		if (vehicle instanceof Car) {
			this.cars --;
		} else {
			this.sleds --;
		}
		if (this.cars == 0 && this.sleds == 0) {
			this.tunnelDirection = null;
		}
	}
	
	//method used to loop through collection of tunnels to see if there is one free for priorityScheduler
	public synchronized boolean canEnter(Vehicle vehicle) {
		if (vehicle instanceof Ambulance) {
			if (this.ambulance != 0) {
				return false;
			}
		}
		
		if (vehicle instanceof Car) {
			if (this.cars == 3) {
				return false;
			} 
		}
		if (vehicle instanceof Sled) {
			if (this.sleds == 1) {
				return false;
			}
		}

		return true;
	}
	
}