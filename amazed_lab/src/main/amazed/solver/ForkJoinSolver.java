package amazed.solver;

import amazed.maze.Maze;

import java.util.LinkedList;
import java.util.Collections;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.atomic.AtomicIntegerArray;

/**
 * <code>ForkJoinSolver</code> implements a solver for
 * <code>Maze</code> objects using a fork/join multi-thread
 * depth-first search.
 * <p>
 * Instances of <code>ForkJoinSolver</code> should be run by a
 * <code>ForkJoinPool</code> object.
 */


public class ForkJoinSolver
    extends SequentialSolver
{

    
    private List<ForkJoinSolver> forkList = new ArrayList<ForkJoinSolver>();
    static private boolean goalFound = false;
    static private ConcurrentSkipListSet<Integer> reserved = new ConcurrentSkipListSet<>();
    private Set <Integer> thisReserved = new HashSet<>();

    /**
     * Creates a solver that searches in <code>maze</code> from the
     * start node to a goal.
     *
     * @param maze   the maze to be searched
     */
    public ForkJoinSolver(Maze maze)
    {
        super(maze);
    }

    /**
     * Creates a solver that searches in <code>maze</code> from the
     * start node to a goal, forking after a given number of visited
     * nodes.
     *
     * @param maze        the maze to be searched
     * @param forkAfter   the number of steps (visited nodes) after
     *                    which a parallel task is forked; if
     *                    <code>forkAfter &lt;= 0</code> the solver never
     *                    forks new tasks
     */
    public ForkJoinSolver(Maze maze, int forkAfter)
    {
        this(maze);
        this.forkAfter = forkAfter;
    }
    public ForkJoinSolver (Maze maze, Set<Integer> visited, int start){
        this(maze);
        this.visited = visited;
        this.start = start;
    }

    /**
     * Searches for and returns the path, as a list of node
     * identifiers, that goes from the start node to a goal node in
     * the maze. If such a path cannot be found (because there are no
     * goals, or all goals are unreacheable), the method returns
     * <code>null</code>.
     *
     * @return   the list of node identifiers from the start node to a
     *           goal node in the maze; <code>null</code> if such a path cannot
     *           be found.
     */
    @Override
    public List<Integer> compute()
    {
        return parallelSearch();
    }

    private List<Integer> parallelSearch() {

        // one player active on the maze at start
        int player = maze.newPlayer(start);

        // start with start node
        frontier.push(start);

        // as long as not all nodes have been processed
        while (!frontier.empty() && !goalFound) {
            // get the new node to process
            int current = frontier.pop();

            // if current node has a goal
            if (maze.hasGoal(current)) {

                goalFound = true;
                // move player to goal
                maze.move(player, current);
                System.out.println(forkList);

                // search finished: reconstruct and return path
                
                start = maze.start();
                return pathFromTo(start, current);
            }
            // if current node has not been visited yet
            if (!visited.contains(current)) {
                // move player to current node
                visited.add(current);
            
                maze.move(player, current);
                
                // mark node as visited
                
                // for every node nb adjacent to current
        
                List<Integer> availablePaths = availablePaths(current);

                for (int i = 0; i < availablePaths.size(); i++) {
                    int nb = availablePaths.get(i);

                    if(reserved.add(nb)){ // Kan bytas mot visited om vi byter till concurrentskiplist set Checks the reserved list to see if some other fork have reserved that spot
                        if (availablePaths.size() - i > 1) {
                            //reserved.add(nb);
                            System.out.println(reserved.size());
                            ForkJoinSolver subtask = new ForkJoinSolver(maze, visited, nb);
                        
                            subtask.predecessor = new HashMap<>(predecessor);
                            subtask.predecessor.put(nb, current);

                            subtask.fork();
                            forkList.add(subtask);

                        } else if (!visited.contains(nb)){
                            frontier.push(nb);
                            predecessor.put(nb, current);
                        }
                    }
                }
            }
        }
        
        for (ForkJoinSolver subtask : forkList) {
           if (subtask.join() != null){
                return subtask.join();
           }
         }
         return null;
        // all nodes explored, no goal found
        
        }

        // Method to return the Nodes which are not visited yet
        private List<Integer> availablePaths (Integer current) {

            List<Integer> availableNeighbours = new ArrayList<>();

            for (Integer nb : maze.neighbors(current)) {
                if(!visited.contains(nb) && !reserved.contains(nb)){
                  //
                    //reserved.add(nb);
                    //thisReserved.add(nb);
                    availableNeighbours.add(nb);
                }
            }
            return availableNeighbours;
        }



    protected List<Integer> pathFromTo(int from, int to) {
        List<Integer> path = new LinkedList<>();
        Integer current = to;
        while (current != from) {
            path.add(current);
            current = predecessor.get(current);
            if (current == null)
                return null;
        }
        path.add(from);
        Collections.reverse(path);
        return path;
    }
}
