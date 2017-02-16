package unnonouno.treedist;

public interface EditScore
{
	double replace(int node1, int node2);

	double delete(int node1);

	double insert(int node2);
}
