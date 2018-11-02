package edu.upf.taln.textplanning.treeeditdistance;

public interface Tree
{
	int NOT_FOUND = -1;

	int getRoot();

	int getFirstChild(int nodeId);

	int getNextSibling(int nodeId);

	int getParent(int nodeId);

	int size();
}
