package amazed.solver;

import amazed.maze.Maze;

import java.util.LinkedList;
import java.util.Collections;
import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListSet;


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

    //Alla processorer har en egen lista med sina subtasks
    private List<ForkJoinSolver> forkList = new ArrayList<ForkJoinSolver>();
    //Check för att meddela att målet är funnet               
    static private boolean goalFound = false;    
    //Lista för att reservera en plats om det inte finns nog med processorer redo att starta                                          
    static private ConcurrentSkipListSet<Integer> reserved = new ConcurrentSkipListSet<>(); 

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

        // En spelare spawnar varje gång vi startar mazen och när vi delar upp i subtasks
        int player = maze.newPlayer(start);

        // Lägg till noden som spelaren spawna på
        frontier.push(start);

        // Sålänge alla noder runtom en inte är besökta och målet inte är funnet så fortsätter loopen
        while (!frontier.empty() && !goalFound) {
            // Tar ut noden som spelaren står på för att...
            int current = frontier.pop();

            // kika om det är målet...
            if (maze.hasGoal(current)) {
                // Säg åt dom andra trådarna att målet är hittat
                goalFound = true; 

                maze.move(player, current);
                // Ändrar start till den egentliga startpunkten så att pathFromTo returnerar korrekt väg
                start = maze.start();
                return pathFromTo(start, current);
            }
            // ..eller om den ska markeras som besökt
            if (!visited.contains(current)) {

                visited.add(current);   
                maze.move(player, current);
                
        
                List<Integer> availablePaths = availablePaths(current);

                for (int i = 0; i < availablePaths.size(); i++) {

                    int nb = availablePaths.get(i);
           
                        //Gör detta om det är fler än två håll att gå                     
                        if (availablePaths.size() - i > 1) { 

                            ForkJoinSolver subtask = new ForkJoinSolver(maze, visited, nb);
                            
                            subtask.predecessor = predecessor;
                            subtask.predecessor.put(nb, current);

                            subtask.fork();
                            //Listan med sig själv och processorns subtasks
                            forkList.add(subtask);                 
                        } else {        
                            frontier.push(nb);
                            predecessor.put(nb, current);
                        }
                }
            }
        }
        //När en processor är klar så vill den vänta på sina subtasks
        for (ForkJoinSolver subtask : forkList){ 
           if (subtask.join() != null){
                return subtask.join();
           }
         }
         return null;
        
        }

        // Hjälpfunktion att returnera en lista med alla grannar som är obesökta och inte reserverade
        private List<Integer> availablePaths (Integer current) {

            List<Integer> availableNeighbours = new ArrayList<>();

            for (Integer nb : maze.neighbors(current)) {
                // Kikar om någon subtask har reserverat platsen och lägger till om den inte är reserverad
                if(!visited.contains(nb) && reserved.add(nb)){
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
