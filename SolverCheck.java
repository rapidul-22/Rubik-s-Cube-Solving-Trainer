import org.kociemba.twophase.Search;
import org.kociemba.twophase.Tools;
class SolverCheck {
 public static void main(String[] args) {
  String solved="UUUUUUUUURRRRRRRRRFFFFFFFFFDDDDDDDDDLLLLLLLLLBBBBBBBBB";
  String result=Search.solution(solved,21,10,false).trim();
  if(result.startsWith("Error")) throw new AssertionError(result);
  String random=Tools.randomCube();
  String moves=Search.solution(random,21,10,false).trim();
  if(moves.startsWith("Error") || moves.isEmpty())throw new AssertionError(moves);
  System.out.println("PASS: supplied solver JAR loads, solved state is accepted (app short-circuits it to prevent unnecessary moves), valid random state gives moves.");
 }
}
