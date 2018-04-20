package edu.upf.taln.textplanning.utils;

import java.io.*;
import java.nio.file.Path;

// Code taken from https://www.journaldev.com/2452/serialization-in-java
public class Serializer
{
	// deserialize to Object from given file
	public static Object deserialize(Path input) throws IOException,
			ClassNotFoundException
	{
		FileInputStream fis = new FileInputStream(input.toString());
		ObjectInputStream ois = new ObjectInputStream(fis);
		Object obj = ois.readObject();
		ois.close();
		return obj;
	}

	// serialize the given object and save it to file
	public static void serialize(Object obj, Path output)
			throws IOException
	{
		FileOutputStream fos = new FileOutputStream(output.toString());
		ObjectOutputStream oos = new ObjectOutputStream(fos);
		oos.writeObject(obj);

		fos.close();
	}
}