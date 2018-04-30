/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package bot;

import ai.abstraction.AbstractAction;
import ai.abstraction.AbstractionLayerAI;
import ai.abstraction.Harvest;
import ai.abstraction.pathfinding.AStarPathFinding;
import ai.core.AI;
import ai.abstraction.pathfinding.PathFinding;
import ai.core.ParameterSpecification;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;
import rts.GameState;
import rts.PhysicalGameState;
import rts.Player;
import rts.PlayerAction;
import rts.units.*;


public class UltraSuperMegaHyperBlaster extends AbstractionLayerAI {

    
	
    //Declares the units that I will be using
    protected UnitTypeTable utt;
    UnitType workerType;
    UnitType baseType;
    UnitType barracksType;
    UnitType rangedType;
    
    //Keeps track of the amount of workers I will use
    static int prodWorkerAmount = 0;

    public UltraSuperMegaHyperBlaster(UnitTypeTable a_utt) {
        this(a_utt, new AStarPathFinding()); //Use A* Pathfinding as this was the most effective 
    }


    public UltraSuperMegaHyperBlaster(UnitTypeTable a_utt, PathFinding a_pf) {
        super(a_pf);
        reset(a_utt);
    }

    public void reset() {
    	super.reset();
    }
    
    public void reset(UnitTypeTable a_utt) {
        utt = a_utt;
        workerType = utt.getUnitType("Worker");
        baseType = utt.getUnitType("Base");
        barracksType = utt.getUnitType("Barracks");
        rangedType = utt.getUnitType("Ranged");
    }

    public AI clone() {
        return new UltraSuperMegaHyperBlaster(utt, pf);
    }

    public PlayerAction getAction(int player, GameState gs) {
        PhysicalGameState pgs = gs.getPhysicalGameState();
        Player p = gs.getPlayer(player);


        // Base Behaviour
        for (Unit u : pgs.getUnits()) {
            if (u.getType() == baseType
                    && u.getPlayer() == player
                    && gs.getActionAssignment(u) == null) {
                baseBehavior(u, p, pgs);
            }
        }

        // barracks Behaviour
        for (Unit u : pgs.getUnits()) {
            if (u.getType() == barracksType
                    && u.getPlayer() == player
                    && gs.getActionAssignment(u) == null) {
                barracksBehaviour(u, p, pgs);
            }
        }

        // Ranged Behaviour
        for (Unit u : pgs.getUnits()) {
            if (u.getType().canAttack && !u.getType().canHarvest
                    && u.getPlayer() == player
                    && gs.getActionAssignment(u) == null) {
                rangedUnitBehavior(u, p, gs);
            }
        }
        
        // Melee Behaviour
        for (Unit u : pgs.getUnits()) {
            if (u.getType().canAttack && !u.getType().canHarvest
                    && u.getPlayer() == player
                    && gs.getActionAssignment(u) == null) {
                meleeUnitBehavior(u, p, gs);
            }
        }
        
        // Worker Behaviour
        List<Unit> workers = new LinkedList<Unit>();
        for (Unit u : pgs.getUnits()) {
            if (u.getType().canHarvest
                    && u.getPlayer() == player) {
                workers.add(u);
                workersBehavior(workers, p, pgs);
            }
        }
        

        //Creates the actions
        return translateActions(player, gs);
    }

    //Declares the base beahviour in game
    public void baseBehavior(Unit u, Player p, PhysicalGameState pgs) {
        int nworkers = 0;
        for (Unit u2 : pgs.getUnits()) {
            if (u2.getType() == workerType
                    && u2.getPlayer() == p.getID()) {
                nworkers++;
            }
        }
        //If there are less workers and more resources than the cost of building them, train till there is 4
        if (nworkers < 4 && p.getResources() >= workerType.cost) {
            train(u, workerType);
        }
    }
    //Barracks purely trains ranged as Workers double as attack later in game
    public void barracksBehaviour(Unit u, Player p, PhysicalGameState pgs) {
        if (p.getResources() >= rangedType.cost) {
            train(u, rangedType);
        }
    }

    //Ranged behaviour is declared here
    public void rangedUnitBehavior(Unit u, Player p, GameState gs) {
        PhysicalGameState pgs = gs.getPhysicalGameState();
        Unit closestBase = null;
        int closestDistance = 3; //Sets closest distance to 3 as to attack at maximum range
        for (Unit u2 : pgs.getUnits()) {
            if (u2.getPlayer() >= 0 && u2.getPlayer() != p.getID()) {
                int d = Math.abs(u2.getX() - u.getX()) + Math.abs(u2.getY() - u.getY());
                if (closestBase == null || d < closestDistance) {
                	closestBase = u2;
                    closestDistance = d;
                }
            }
        }
        if (closestBase != null) { //Will make  beeline for the nearest enemy base, but will attack enemies if met on path

            attack(u, closestBase);
        }
    }
    
    
    public void meleeUnitBehavior(Unit u, Player p, GameState gs) {
        PhysicalGameState pgs = gs.getPhysicalGameState();
        Unit closestEnemy = null;
        int closestDistance = 0;
        for (Unit u2 : pgs.getUnits()) {
            if (u2.getPlayer() >= 0 && u2.getPlayer() != p.getID()) {
                int d = Math.abs(u2.getX() - u.getX()) + Math.abs(u2.getY() - u.getY());
                if (closestEnemy == null || d < closestDistance) {
                    closestEnemy = u2;
                    closestDistance = d;
                }
            }
        }
        if (closestEnemy != null) //Worker simply attacks the nearest enemy 
        {
            attack(u, closestEnemy);
        }
    }

    public void workersBehavior(List<Unit> workers, Player p, PhysicalGameState pgs) {
    	
    	
        int nbarracks = 0; //Number of built barracks
        int attackNeeds = 4; //Sets the number of attack units needed
        Unit AttackWorker = null; //Sets another unit for attack 

        List<Unit> freeWorkers = new LinkedList<Unit>(); //Creates two lists for the workers 
        List<Unit> fightWorkers = new LinkedList<Unit>();
        
        //Locations of bases and barracks to build on
        List<Integer> reservedPositions = new LinkedList<Integer>();
        
        freeWorkers.addAll(workers); //All workers are added to free at the start 

        if (workers.isEmpty()) 
        {
            return;
        }

   
        
        if (attackNeeds < freeWorkers.size()) //If there are more free workers than attack, add them to attack list 
        {
        	AttackWorker = (freeWorkers.remove(0));
        	fightWorkers.add(AttackWorker);
        }
        
        
        for (Unit u2 : pgs.getUnits()) //Adds built barracks to the number of barracks 
        {
            if (u2.getType() == barracksType
                && u2.getPlayer() == p.getID()) 
            {
                nbarracks++;
            }
        }

        //Build Barracks if there isn't one
        if (nbarracks < 1) 
        	{
            	Unit u = freeWorkers.remove(0);
                buildIfNotAlreadyBuilding(u,barracksType,u.getX(),u.getY(),reservedPositions,p,pgs);
            }


        // Free worker units harvest and deposit
        for (Unit u : freeWorkers) {
            Unit closestBase = null;
            Unit closestResource = null;
            int closestDistance = 0;
            for (Unit u2 : pgs.getUnits()) {
                if (u2.getType().isResource) {
                    int d = Math.abs(u2.getX() - u.getX()) + Math.abs(u2.getY() - u.getY());
                    if (closestResource == null || d < closestDistance) {
                        closestResource = u2;
                        closestDistance = d;
                    }
                }
            }
            
           
            for (Unit u2 : pgs.getUnits()) {
                if (u2.getType().isStockpile && u2.getPlayer()==p.getID()) {
                    int d = Math.abs(u2.getX() - u.getX()) + Math.abs(u2.getY() - u.getY());
                    if (closestBase == null || d < closestDistance) {
                        closestBase = u2;
                        closestDistance = d;
                    }
                }
            }
            
            if (closestResource != null && closestBase != null) {
                AbstractAction aa = getAbstractAction(u);
                if (aa instanceof Harvest) {
                    Harvest h_aa = (Harvest)aa;
                    if (h_aa.getTarget() != closestResource || h_aa.getBase()!=closestBase) harvest(u, closestResource, closestBase);
                } else {
                    harvest(u, closestResource, closestBase);
                }
            }
        }
          
    }

   
    @Override
    public List<ParameterSpecification> getParameters()
    {
        List<ParameterSpecification> parameters = new ArrayList<>();
        
        parameters.add(new ParameterSpecification("PathFinding", PathFinding.class, new AStarPathFinding()));

        return parameters;
    }
}
