package cs131.pa2.CarsTunnels;

import cs131.pa2.Abstract.Tunnel;
import cs131.pa2.Abstract.Vehicle;
import cs131.pa2.Abstract.Direction;
import java.util.*;

public class BasicTunnel extends Tunnel{

	protected Direction tunnelDirection; //needed to keep track of the current direction of the one road in tunnel 
	protected int sleds; //needed to keep track of the number of sleds in the tunnel. Should never exceed 1
	protected int cars; //needed to keep track of the number of cars in the tunnel. Should never exceed 3
	protected List<Vehicle> tunnelVehicles; //keep track of the vehicles in the tunnel in case needed in pt 2
	
	public BasicTunnel(String name) {
		super(name);
		this.sleds = 0;
		this.cars = 0;
		this.tunnelDirection = null; //no direction is determined yet when tunnel is first created
		this.tunnelVehicles = new ArrayList<>();
	}

	@Override
	public synchronized boolean tryToEnterInner(Vehicle vehicle) {
		//first check what type of vehicle this is
		//vehicle = car, then we need to check that no sled is inside, and the #
		//of cars is < 3
		if (vehicle instanceof Car) {
			if (this.sleds > 0 || this.cars == 3) {
				return false;
			} else if (tunnelDirection == null || tunnelDirection.equals(vehicle.getDirection())){
				this.cars ++;
				this.tunnelDirection = vehicle.getDirection();
				this.tunnelVehicles.add(vehicle);
				return true;
			} else {
				return false;
			}
		} else { //vehicle is an instance of Sled. We check if there is already a sled, or there is a car
			if (this.sleds > 0 || this.cars > 0) {
				return false;
			} else if (tunnelDirection == null || tunnelDirection.equals(vehicle.getDirection())){
				this.sleds ++;
				this.tunnelDirection = vehicle.getDirection();
				this.tunnelVehicles.add(vehicle);
				return true;
			} else {
				return false;
			}
		}
	}

	@Override
	public synchronized void exitTunnelInner(Vehicle vehicle) {
		if (vehicle instanceof Car) {
			this.cars --;
			this.tunnelVehicles.remove(vehicle);
		} else {
			this.sleds --;
			this.tunnelVehicles.remove(vehicle);
		}
		if (this.cars == 0 && this.sleds == 0) {
			this.tunnelDirection = null;
		}
	}
	
}