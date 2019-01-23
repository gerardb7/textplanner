package edu.upf.taln.textplanning.uima;

import java.util.HashMap;
import java.util.Map;

public class TypeMapper
{
	public enum Type
	{
		Thing, Location,
		Incident, Flood, Crack, Collapse, Overflow, Fire, Smoke, Empty, Full, TrafficJam,
		VulnerableObject, Asset, LivingBeing, Bridge, River, Square, Car, Vehicle, Person,
		Sewer, Levee, Forest, Park, Venue, Building
	}

	private final static Map<String, Type> meanings2types = new HashMap<String, Type>()
	{
		{

		}
	};

	// Used to guarantee backwards compatibility with the pilot of the first prototype
	private final static Map<String, Type> forms2types = new HashMap<String, Type>()
	{
		{
			put("piazza", Type.Square);
			put("allagata", Type.Flood);
			put("allagare", Type.Flood);
			put("alluvione", Type.Flood);
			put("allagamento", Type.Flood);
			put("floode", Type.Flood);
			put("fognatura", Type.Sewer);
			put("argine", Type.Levee);
			put("argini", Type.Levee);
			put("traboccare", Type.Overflow);
			put("traboccato", Type.Overflow);
			put("ponte", Type.Bridge);
			put("crepe", Type.Crack);
			put("crepa", Type.Crack);
			put("spaccare", Type.Crack);
			put("spaccati", Type.Crack);
			put("crollare", Type.Collapse);
			put("crollati", Type.Collapse);
			put("fiume", Type.River);

			put("parque", Type.Park);
			put("incendio", Type.Fire);
			put("fuego", Type.Fire);
			put("bosque", Type.Forest);

			put("αίθουσα", Type.Venue); // feminine
			put("κτίριο", Type.Building); // neuter
			put("κτήριο", Type.Building); // neuter
			put("χώρος", Type.Venue); // masculine
			put("δημαρχείο", Type.Building); // city hall, neuter
			put("δημαρχείου", Type.Building); // city hall, neuter+possessive
			put("άδειος", Type.Empty); // masculine
			put("άδεια", Type.Empty); // feminine
			put("άδειο", Type.Empty); // neuter
			put("γεμάτο", Type.Full); // masculine
			put("γεμάτη", Type.Full); // feminine
			put("γεμάτος", Type.Full); // neuter
			put("ΚΑΠΗ", Type.Venue); // feminine
		}
	};


	public static String map(String meaning, String text, boolean isPredicate)
	{
		if (meanings2types.containsKey(meaning))
			return meanings2types.get(meaning).toString();

		if (forms2types.containsKey(text))
			return forms2types.get(text).toString();

		if (isPredicate)
			return Type.Incident.toString();
		else
			return Type.Thing.toString();
	}

	public static boolean isIncident(String type)
	{
		switch(Type.valueOf(type))
		{
			case Incident:
			case Flood:
			case Crack:
			case Collapse:
			case Overflow:
			case Fire:
			case Smoke:
			case Empty:
			case Full:
			case TrafficJam:
				return true;
			default:
				return false;
		}
	}

	public static boolean isVulnerableObject(String type)
	{
		switch(Type.valueOf(type))
		{
			case VulnerableObject:
			case Asset:
			case LivingBeing:
			case Bridge:
			case River:
			case Square:
			case Car:
			case Vehicle:
			case Person:
			case Sewer:
			case Levee:
			case Forest:
			case Park:
			case Venue:
			case Building:
				return true;
			default:
				return false;
		}
	}
}
