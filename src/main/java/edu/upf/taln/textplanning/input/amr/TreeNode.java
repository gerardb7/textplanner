package edu.upf.taln.textplanning.input.amr;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class TreeNode implements Iterable<TreeNode> {
    public String text;
    public int offset;
    public List<TreeNode> elements;

    Map<Label, TreeNode> labelled;

    public TreeNode() {
        this("", -1, new ArrayList<TreeNode>(0));
    }

    public TreeNode(String text, int offset) {
        this(text, offset, new ArrayList<TreeNode>(0));
    }

    public TreeNode(String text, int offset, List<TreeNode> elements) {
        this.text = text;
        this.offset = offset;
        this.elements = elements;
        this.labelled = new EnumMap<Label, TreeNode>(Label.class);
    }

    public TreeNode get(Label key) {
        return labelled.get(key);
    }

    public Iterator<TreeNode> iterator() {
        return elements.iterator();
    }
}

class TreeNode1 extends TreeNode {
    TreeNode1(String text, int offset, List<TreeNode> elements) {
        super(text, offset, elements);
        labelled.put(Label.os, elements.get(8));
        labelled.put(Label.var, elements.get(2));
        labelled.put(Label.s, elements.get(5));
        labelled.put(Label.aconcept, elements.get(6));
    }
}

class TreeNode2 extends TreeNode {
    TreeNode2(String text, int offset, List<TreeNode> elements) {
        super(text, offset, elements);
        labelled.put(Label.s, elements.get(0));
        labelled.put(Label.desc, elements.get(1));
    }
}

class TreeNode3 extends TreeNode {
    TreeNode3(String text, int offset, List<TreeNode> elements) {
        super(text, offset, elements);
        labelled.put(Label.rel, elements.get(0));
        labelled.put(Label.s, elements.get(2));
        labelled.put(Label.y, elements.get(3));
    }
}

class TreeNode4 extends TreeNode {
    TreeNode4(String text, int offset, List<TreeNode> elements) {
        super(text, offset, elements);
        labelled.put(Label.var, elements.get(0));
    }
}

class TreeNode5 extends TreeNode {
    TreeNode5(String text, int offset, List<TreeNode> elements) {
        super(text, offset, elements);
        labelled.put(Label.namedconst, elements.get(0));
    }
}

class TreeNode6 extends TreeNode {
    TreeNode6(String text, int offset, List<TreeNode> elements) {
        super(text, offset, elements);
        labelled.put(Label.str, elements.get(0));
    }
}

class TreeNode7 extends TreeNode {
    TreeNode7(String text, int offset, List<TreeNode> elements) {
        super(text, offset, elements);
        labelled.put(Label.num, elements.get(0));
    }
}

class TreeNode8 extends TreeNode {
    TreeNode8(String text, int offset, List<TreeNode> elements) {
        super(text, offset, elements);
        labelled.put(Label.concept, elements.get(0));
    }
}
