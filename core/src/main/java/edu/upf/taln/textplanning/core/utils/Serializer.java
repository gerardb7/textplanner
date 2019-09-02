package edu.upf.taln.textplanning.core.utils;

import java.io.*;
import java.nio.file.Path;

// Code taken from https://www.journaldev.com/2452/serialization-in-java
public class Serializer
{
	// utility class to recover serialized objects which have been moved to a different package
//	private static class RecoverStream extends ObjectInputStream
//	{
//
//		public RecoverStream(InputStream in) throws IOException { super(in); }
//
//		@Override
//		protected java.io.ObjectStreamClass readClassDescriptor()
//				throws IOException, ClassNotFoundException
//		{
//			ObjectStreamClass desc = super.readClassDescriptor();
//			switch (desc.getName())
//			{
//				case "edu.upf.taln.textplanning.structures.io.GraphList":
//					return ObjectStreamClass.lookup(edu.upf.taln.textplanning.io.GraphList.class);
//				case "edu.upf.taln.textplanning.structures.io.Candidate":
//					return ObjectStreamClass.lookup(edu.upf.taln.textplanning.io.Candidate.class);
//				case "edu.upf.taln.textplanning.structures.io.Candidate$FunctionType":
//					return ObjectStreamClass.lookup(edu.upf.taln.textplanning.io.Candidate.FunctionType.class);
//				case "edu.upf.taln.textplanning.structures.io.CoreferenceChain":
//					return ObjectStreamClass.lookup(edu.upf.taln.textplanning.io.CoreferenceChain.class);
//				case "edu.upf.taln.textplanning.structures.io.SemanticGraph":
//					return ObjectStreamClass.lookup(edu.upf.taln.textplanning.io.SemanticGraph.class);
//			}
//			return desc;
//		}
//	}


	// deserialize to Object from given file
	public static Object deserialize(Path input) throws IOException,
			ClassNotFoundException
	{
		FileInputStream fis = new FileInputStream(input.toString());
		// RecoverStream ois = new RecoverStream(fis);
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