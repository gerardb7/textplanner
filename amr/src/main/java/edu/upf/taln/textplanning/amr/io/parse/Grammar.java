package edu.upf.taln.textplanning.amr.io.parse;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

abstract class Grammar {
    static TreeNode FAILURE = new TreeNode();

    int inputSize, offset, failure;
    String input;
    List<String> expected;
    Map<Label, Map<Integer, CacheRecord>> cache;
    Actions actions;

    private static Pattern REGEX_1 = Pattern.compile("\\A[^ \\t\\n\\r]");
    private static Pattern REGEX_2 = Pattern.compile("\\A[a-z]");
    private static Pattern REGEX_3 = Pattern.compile("\\A[0-9]");
    private static Pattern REGEX_4 = Pattern.compile("\\A[a-z]");
    private static Pattern REGEX_5 = Pattern.compile("\\A[a-z]");
    private static Pattern REGEX_6 = Pattern.compile("\\A[ \\t\\n\\r]");
    private static Pattern REGEX_7 = Pattern.compile("\\A[+-]");
    private static Pattern REGEX_8 = Pattern.compile("\\A[0-9]");
    private static Pattern REGEX_9 = Pattern.compile("\\A[^\\\"\\s]");
    private static Pattern REGEX_10 = Pattern.compile("\\A[^\\\"\\n\\r]");
    private static Pattern REGEX_11 = Pattern.compile("\\A[^\\\"\\s]");
    private static Pattern REGEX_12 = Pattern.compile("\\A[+-]");
    private static Pattern REGEX_13 = Pattern.compile("\\A[0-9]");
    private static Pattern REGEX_14 = Pattern.compile("\\A[0-9]");
    private static Pattern REGEX_15 = Pattern.compile("\\A[^) \\t\\n\\r]");
    private static Pattern REGEX_16 = Pattern.compile("\\A[A-Za-z0-9.,]");
    private static Pattern REGEX_17 = Pattern.compile("\\A[ \\t]");
    private static Pattern REGEX_18 = Pattern.compile("\\A[\\n\\r]");
    private static Pattern REGEX_19 = Pattern.compile("\\A[ \\t]");
    private static Pattern REGEX_20 = Pattern.compile("\\A[ \\t]");
    private static Pattern REGEX_21 = Pattern.compile("\\A[ \\t]");
    private static Pattern REGEX_22 = Pattern.compile("\\A[\\n\\r]");
    private static Pattern REGEX_23 = Pattern.compile("\\A[ \\t]");

    TreeNode _read_x() {
        TreeNode address0 = FAILURE;
        int index0 = offset;
        Map<Integer, CacheRecord> rule = cache.get(Label.x);
        if (rule == null) {
            rule = new HashMap<Integer, CacheRecord>();
            cache.put(Label.x, rule);
        }
        if (rule.containsKey(offset)) {
            address0 = rule.get(offset).node;
            offset = rule.get(offset).tail;
        } else {
            int index1 = offset;
            List<TreeNode> elements0 = new ArrayList<TreeNode>(10);
            TreeNode address1 = FAILURE;
            String chunk0 = null;
            if (offset < inputSize) {
                chunk0 = input.substring(offset, offset + 1);
            }
            if (chunk0 != null && chunk0.equals("(")) {
                address1 = new TreeNode(input.substring(offset, offset + 1), offset);
                offset = offset + 1;
            } else {
                address1 = FAILURE;
                if (offset > failure) {
                    failure = offset;
                    expected = new ArrayList<String>();
                }
                if (offset == failure) {
                    expected.add("\"(\"");
                }
            }
            if (address1 != FAILURE) {
                elements0.add(0, address1);
                TreeNode address2 = FAILURE;
                address2 = _read_os();
                if (address2 != FAILURE) {
                    elements0.add(1, address2);
                    TreeNode address3 = FAILURE;
                    address3 = _read_var();
                    if (address3 != FAILURE) {
                        elements0.add(2, address3);
                        TreeNode address4 = FAILURE;
                        address4 = _read_s();
                        if (address4 != FAILURE) {
                            elements0.add(3, address4);
                            TreeNode address5 = FAILURE;
                            String chunk1 = null;
                            if (offset < inputSize) {
                                chunk1 = input.substring(offset, offset + 1);
                            }
                            if (chunk1 != null && chunk1.equals("/")) {
                                address5 = new TreeNode(input.substring(offset, offset + 1), offset);
                                offset = offset + 1;
                            } else {
                                address5 = FAILURE;
                                if (offset > failure) {
                                    failure = offset;
                                    expected = new ArrayList<String>();
                                }
                                if (offset == failure) {
                                    expected.add("\"/\"");
                                }
                            }
                            if (address5 != FAILURE) {
                                elements0.add(4, address5);
                                TreeNode address6 = FAILURE;
                                address6 = _read_s();
                                if (address6 != FAILURE) {
                                    elements0.add(5, address6);
                                    TreeNode address7 = FAILURE;
                                    address7 = _read_aconcept();
                                    if (address7 != FAILURE) {
                                        elements0.add(6, address7);
                                        TreeNode address8 = FAILURE;
                                        int remaining0 = 0;
                                        int index2 = offset;
                                        List<TreeNode> elements1 = new ArrayList<TreeNode>();
                                        TreeNode address9 = new TreeNode("", -1);
                                        while (address9 != FAILURE) {
                                            int index3 = offset;
                                            List<TreeNode> elements2 = new ArrayList<TreeNode>(2);
                                            TreeNode address10 = FAILURE;
                                            address10 = _read_s();
                                            if (address10 != FAILURE) {
                                                elements2.add(0, address10);
                                                TreeNode address11 = FAILURE;
                                                address11 = _read_desc();
                                                if (address11 != FAILURE) {
                                                    elements2.add(1, address11);
                                                } else {
                                                    elements2 = null;
                                                    offset = index3;
                                                }
                                            } else {
                                                elements2 = null;
                                                offset = index3;
                                            }
                                            if (elements2 == null) {
                                                address9 = FAILURE;
                                            } else {
                                                address9 = new TreeNode2(input.substring(index3, offset), index3, elements2);
                                                offset = offset;
                                            }
                                            if (address9 != FAILURE) {
                                                elements1.add(address9);
                                                --remaining0;
                                            }
                                        }
                                        if (remaining0 <= 0) {
                                            address8 = new TreeNode(input.substring(index2, offset), index2, elements1);
                                            offset = offset;
                                        } else {
                                            address8 = FAILURE;
                                        }
                                        if (address8 != FAILURE) {
                                            elements0.add(7, address8);
                                            TreeNode address12 = FAILURE;
                                            address12 = _read_os();
                                            if (address12 != FAILURE) {
                                                elements0.add(8, address12);
                                                TreeNode address13 = FAILURE;
                                                String chunk2 = null;
                                                if (offset < inputSize) {
                                                    chunk2 = input.substring(offset, offset + 1);
                                                }
                                                if (chunk2 != null && chunk2.equals(")")) {
                                                    address13 = new TreeNode(input.substring(offset, offset + 1), offset);
                                                    offset = offset + 1;
                                                } else {
                                                    address13 = FAILURE;
                                                    if (offset > failure) {
                                                        failure = offset;
                                                        expected = new ArrayList<String>();
                                                    }
                                                    if (offset == failure) {
                                                        expected.add("\")\"");
                                                    }
                                                }
                                                if (address13 != FAILURE) {
                                                    elements0.add(9, address13);
                                                } else {
                                                    elements0 = null;
                                                    offset = index1;
                                                }
                                            } else {
                                                elements0 = null;
                                                offset = index1;
                                            }
                                        } else {
                                            elements0 = null;
                                            offset = index1;
                                        }
                                    } else {
                                        elements0 = null;
                                        offset = index1;
                                    }
                                } else {
                                    elements0 = null;
                                    offset = index1;
                                }
                            } else {
                                elements0 = null;
                                offset = index1;
                            }
                        } else {
                            elements0 = null;
                            offset = index1;
                        }
                    } else {
                        elements0 = null;
                        offset = index1;
                    }
                } else {
                    elements0 = null;
                    offset = index1;
                }
            } else {
                elements0 = null;
                offset = index1;
            }
            if (elements0 == null) {
                address0 = FAILURE;
            } else {
                address0 = actions.make_ancestor(input, index1, offset, elements0);
                offset = offset;
            }
            rule.put(index0, new CacheRecord(address0, offset));
        }
        return address0;
    }

    TreeNode _read_desc() {
        TreeNode address0 = FAILURE;
        int index0 = offset;
        Map<Integer, CacheRecord> rule = cache.get(Label.desc);
        if (rule == null) {
            rule = new HashMap<Integer, CacheRecord>();
            cache.put(Label.desc, rule);
        }
        if (rule.containsKey(offset)) {
            address0 = rule.get(offset).node;
            offset = rule.get(offset).tail;
        } else {
            int index1 = offset;
            List<TreeNode> elements0 = new ArrayList<TreeNode>(4);
            TreeNode address1 = FAILURE;
            address1 = _read_rel();
            if (address1 != FAILURE) {
                elements0.add(0, address1);
                TreeNode address2 = FAILURE;
                int index2 = offset;
                address2 = _read_alignment();
                if (address2 == FAILURE) {
                    address2 = new TreeNode(input.substring(index2, index2), index2);
                    offset = index2;
                }
                if (address2 != FAILURE) {
                    elements0.add(1, address2);
                    TreeNode address3 = FAILURE;
                    address3 = _read_s();
                    if (address3 != FAILURE) {
                        elements0.add(2, address3);
                        TreeNode address4 = FAILURE;
                        address4 = _read_y();
                        if (address4 != FAILURE) {
                            elements0.add(3, address4);
                        } else {
                            elements0 = null;
                            offset = index1;
                        }
                    } else {
                        elements0 = null;
                        offset = index1;
                    }
                } else {
                    elements0 = null;
                    offset = index1;
                }
            } else {
                elements0 = null;
                offset = index1;
            }
            if (elements0 == null) {
                address0 = FAILURE;
            } else {
                address0 = actions.make_descendent(input, index1, offset, elements0);
                offset = offset;
            }
            rule.put(index0, new CacheRecord(address0, offset));
        }
        return address0;
    }

    TreeNode _read_rel() {
        TreeNode address0 = FAILURE;
        int index0 = offset;
        Map<Integer, CacheRecord> rule = cache.get(Label.rel);
        if (rule == null) {
            rule = new HashMap<Integer, CacheRecord>();
            cache.put(Label.rel, rule);
        }
        if (rule.containsKey(offset)) {
            address0 = rule.get(offset).node;
            offset = rule.get(offset).tail;
        } else {
            int index1 = offset;
            List<TreeNode> elements0 = new ArrayList<TreeNode>(2);
            TreeNode address1 = FAILURE;
            String chunk0 = null;
            if (offset < inputSize) {
                chunk0 = input.substring(offset, offset + 1);
            }
            if (chunk0 != null && chunk0.equals(":")) {
                address1 = new TreeNode(input.substring(offset, offset + 1), offset);
                offset = offset + 1;
            } else {
                address1 = FAILURE;
                if (offset > failure) {
                    failure = offset;
                    expected = new ArrayList<String>();
                }
                if (offset == failure) {
                    expected.add("\":\"");
                }
            }
            if (address1 != FAILURE) {
                elements0.add(0, address1);
                TreeNode address2 = FAILURE;
                int remaining0 = 1;
                int index2 = offset;
                List<TreeNode> elements1 = new ArrayList<TreeNode>();
                TreeNode address3 = new TreeNode("", -1);
                while (address3 != FAILURE) {
                    String chunk1 = null;
                    if (offset < inputSize) {
                        chunk1 = input.substring(offset, offset + 1);
                    }
                    if (chunk1 != null && REGEX_1.matcher(chunk1).matches()) {
                        address3 = new TreeNode(input.substring(offset, offset + 1), offset);
                        offset = offset + 1;
                    } else {
                        address3 = FAILURE;
                        if (offset > failure) {
                            failure = offset;
                            expected = new ArrayList<String>();
                        }
                        if (offset == failure) {
                            expected.add("[^ \\t\\n\\r]");
                        }
                    }
                    if (address3 != FAILURE) {
                        elements1.add(address3);
                        --remaining0;
                    }
                }
                if (remaining0 <= 0) {
                    address2 = new TreeNode(input.substring(index2, offset), index2, elements1);
                    offset = offset;
                } else {
                    address2 = FAILURE;
                }
                if (address2 != FAILURE) {
                    elements0.add(1, address2);
                } else {
                    elements0 = null;
                    offset = index1;
                }
            } else {
                elements0 = null;
                offset = index1;
            }
            if (elements0 == null) {
                address0 = FAILURE;
            } else {
                address0 = new TreeNode(input.substring(index1, offset), index1, elements0);
                offset = offset;
            }
            rule.put(index0, new CacheRecord(address0, offset));
        }
        return address0;
    }

    TreeNode _read_y() {
        TreeNode address0 = FAILURE;
        int index0 = offset;
        Map<Integer, CacheRecord> rule = cache.get(Label.y);
        if (rule == null) {
            rule = new HashMap<Integer, CacheRecord>();
            cache.put(Label.y, rule);
        }
        if (rule.containsKey(offset)) {
            address0 = rule.get(offset).node;
            offset = rule.get(offset).tail;
        } else {
            int index1 = offset;
            address0 = _read_x();
            if (address0 == FAILURE) {
                offset = index1;
                address0 = _read_anamedconst();
                if (address0 == FAILURE) {
                    offset = index1;
                    address0 = _read_avar();
                    if (address0 == FAILURE) {
                        offset = index1;
                        address0 = _read_astr();
                        if (address0 == FAILURE) {
                            offset = index1;
                            address0 = _read_anum();
                            if (address0 == FAILURE) {
                                offset = index1;
                            }
                        }
                    }
                }
            }
            rule.put(index0, new CacheRecord(address0, offset));
        }
        return address0;
    }

    TreeNode _read_avar() {
        TreeNode address0 = FAILURE;
        int index0 = offset;
        Map<Integer, CacheRecord> rule = cache.get(Label.avar);
        if (rule == null) {
            rule = new HashMap<Integer, CacheRecord>();
            cache.put(Label.avar, rule);
        }
        if (rule.containsKey(offset)) {
            address0 = rule.get(offset).node;
            offset = rule.get(offset).tail;
        } else {
            int index1 = offset;
            List<TreeNode> elements0 = new ArrayList<TreeNode>(2);
            TreeNode address1 = FAILURE;
            address1 = _read_var();
            if (address1 != FAILURE) {
                elements0.add(0, address1);
                TreeNode address2 = FAILURE;
                int index2 = offset;
                address2 = _read_alignment();
                if (address2 == FAILURE) {
                    address2 = new TreeNode(input.substring(index2, index2), index2);
                    offset = index2;
                }
                if (address2 != FAILURE) {
                    elements0.add(1, address2);
                } else {
                    elements0 = null;
                    offset = index1;
                }
            } else {
                elements0 = null;
                offset = index1;
            }
            if (elements0 == null) {
                address0 = FAILURE;
            } else {
                address0 = actions.make_var(input, index1, offset, elements0);
                offset = offset;
            }
            rule.put(index0, new CacheRecord(address0, offset));
        }
        return address0;
    }

    TreeNode _read_var() {
        TreeNode address0 = FAILURE;
        int index0 = offset;
        Map<Integer, CacheRecord> rule = cache.get(Label.var);
        if (rule == null) {
            rule = new HashMap<Integer, CacheRecord>();
            cache.put(Label.var, rule);
        }
        if (rule.containsKey(offset)) {
            address0 = rule.get(offset).node;
            offset = rule.get(offset).tail;
        } else {
            int index1 = offset;
            List<TreeNode> elements0 = new ArrayList<TreeNode>(2);
            TreeNode address1 = FAILURE;
            int remaining0 = 1;
            int index2 = offset;
            List<TreeNode> elements1 = new ArrayList<TreeNode>();
            TreeNode address2 = new TreeNode("", -1);
            while (address2 != FAILURE) {
                String chunk0 = null;
                if (offset < inputSize) {
                    chunk0 = input.substring(offset, offset + 1);
                }
                if (chunk0 != null && REGEX_2.matcher(chunk0).matches()) {
                    address2 = new TreeNode(input.substring(offset, offset + 1), offset);
                    offset = offset + 1;
                } else {
                    address2 = FAILURE;
                    if (offset > failure) {
                        failure = offset;
                        expected = new ArrayList<String>();
                    }
                    if (offset == failure) {
                        expected.add("[a-z]");
                    }
                }
                if (address2 != FAILURE) {
                    elements1.add(address2);
                    --remaining0;
                }
            }
            if (remaining0 <= 0) {
                address1 = new TreeNode(input.substring(index2, offset), index2, elements1);
                offset = offset;
            } else {
                address1 = FAILURE;
            }
            if (address1 != FAILURE) {
                elements0.add(0, address1);
                TreeNode address3 = FAILURE;
                int remaining1 = 0;
                int index3 = offset;
                List<TreeNode> elements2 = new ArrayList<TreeNode>();
                TreeNode address4 = new TreeNode("", -1);
                while (address4 != FAILURE) {
                    String chunk1 = null;
                    if (offset < inputSize) {
                        chunk1 = input.substring(offset, offset + 1);
                    }
                    if (chunk1 != null && REGEX_3.matcher(chunk1).matches()) {
                        address4 = new TreeNode(input.substring(offset, offset + 1), offset);
                        offset = offset + 1;
                    } else {
                        address4 = FAILURE;
                        if (offset > failure) {
                            failure = offset;
                            expected = new ArrayList<String>();
                        }
                        if (offset == failure) {
                            expected.add("[0-9]");
                        }
                    }
                    if (address4 != FAILURE) {
                        elements2.add(address4);
                        --remaining1;
                    }
                }
                if (remaining1 <= 0) {
                    address3 = new TreeNode(input.substring(index3, offset), index3, elements2);
                    offset = offset;
                } else {
                    address3 = FAILURE;
                }
                if (address3 != FAILURE) {
                    elements0.add(1, address3);
                } else {
                    elements0 = null;
                    offset = index1;
                }
            } else {
                elements0 = null;
                offset = index1;
            }
            if (elements0 == null) {
                address0 = FAILURE;
            } else {
                address0 = new TreeNode(input.substring(index1, offset), index1, elements0);
                offset = offset;
            }
            rule.put(index0, new CacheRecord(address0, offset));
        }
        return address0;
    }

    TreeNode _read_anamedconst() {
        TreeNode address0 = FAILURE;
        int index0 = offset;
        Map<Integer, CacheRecord> rule = cache.get(Label.anamedconst);
        if (rule == null) {
            rule = new HashMap<Integer, CacheRecord>();
            cache.put(Label.anamedconst, rule);
        }
        if (rule.containsKey(offset)) {
            address0 = rule.get(offset).node;
            offset = rule.get(offset).tail;
        } else {
            int index1 = offset;
            List<TreeNode> elements0 = new ArrayList<TreeNode>(2);
            TreeNode address1 = FAILURE;
            address1 = _read_namedconst();
            if (address1 != FAILURE) {
                elements0.add(0, address1);
                TreeNode address2 = FAILURE;
                int index2 = offset;
                address2 = _read_alignment();
                if (address2 == FAILURE) {
                    address2 = new TreeNode(input.substring(index2, index2), index2);
                    offset = index2;
                }
                if (address2 != FAILURE) {
                    elements0.add(1, address2);
                } else {
                    elements0 = null;
                    offset = index1;
                }
            } else {
                elements0 = null;
                offset = index1;
            }
            if (elements0 == null) {
                address0 = FAILURE;
            } else {
                address0 = actions.make_constant(input, index1, offset, elements0);
                offset = offset;
            }
            rule.put(index0, new CacheRecord(address0, offset));
        }
        return address0;
    }

    TreeNode _read_namedconst() {
        TreeNode address0 = FAILURE;
        int index0 = offset;
        Map<Integer, CacheRecord> rule = cache.get(Label.namedconst);
        if (rule == null) {
            rule = new HashMap<Integer, CacheRecord>();
            cache.put(Label.namedconst, rule);
        }
        if (rule.containsKey(offset)) {
            address0 = rule.get(offset).node;
            offset = rule.get(offset).tail;
        } else {
            int index1 = offset;
            int index2 = offset;
            List<TreeNode> elements0 = new ArrayList<TreeNode>(3);
            TreeNode address1 = FAILURE;
            String chunk0 = null;
            if (offset < inputSize) {
                chunk0 = input.substring(offset, offset + 1);
            }
            if (chunk0 != null && REGEX_4.matcher(chunk0).matches()) {
                address1 = new TreeNode(input.substring(offset, offset + 1), offset);
                offset = offset + 1;
            } else {
                address1 = FAILURE;
                if (offset > failure) {
                    failure = offset;
                    expected = new ArrayList<String>();
                }
                if (offset == failure) {
                    expected.add("[a-z]");
                }
            }
            if (address1 != FAILURE) {
                elements0.add(0, address1);
                TreeNode address2 = FAILURE;
                int remaining0 = 1;
                int index3 = offset;
                List<TreeNode> elements1 = new ArrayList<TreeNode>();
                TreeNode address3 = new TreeNode("", -1);
                while (address3 != FAILURE) {
                    String chunk1 = null;
                    if (offset < inputSize) {
                        chunk1 = input.substring(offset, offset + 1);
                    }
                    if (chunk1 != null && REGEX_5.matcher(chunk1).matches()) {
                        address3 = new TreeNode(input.substring(offset, offset + 1), offset);
                        offset = offset + 1;
                    } else {
                        address3 = FAILURE;
                        if (offset > failure) {
                            failure = offset;
                            expected = new ArrayList<String>();
                        }
                        if (offset == failure) {
                            expected.add("[a-z]");
                        }
                    }
                    if (address3 != FAILURE) {
                        elements1.add(address3);
                        --remaining0;
                    }
                }
                if (remaining0 <= 0) {
                    address2 = new TreeNode(input.substring(index3, offset), index3, elements1);
                    offset = offset;
                } else {
                    address2 = FAILURE;
                }
                if (address2 != FAILURE) {
                    elements0.add(1, address2);
                    TreeNode address4 = FAILURE;
                    String chunk2 = null;
                    if (offset < inputSize) {
                        chunk2 = input.substring(offset, offset + 1);
                    }
                    if (chunk2 != null && REGEX_6.matcher(chunk2).matches()) {
                        address4 = new TreeNode(input.substring(offset, offset + 1), offset);
                        offset = offset + 1;
                    } else {
                        address4 = FAILURE;
                        if (offset > failure) {
                            failure = offset;
                            expected = new ArrayList<String>();
                        }
                        if (offset == failure) {
                            expected.add("[ \\t\\n\\r]");
                        }
                    }
                    if (address4 != FAILURE) {
                        elements0.add(2, address4);
                    } else {
                        elements0 = null;
                        offset = index2;
                    }
                } else {
                    elements0 = null;
                    offset = index2;
                }
            } else {
                elements0 = null;
                offset = index2;
            }
            if (elements0 == null) {
                address0 = FAILURE;
            } else {
                address0 = new TreeNode(input.substring(index2, offset), index2, elements0);
                offset = offset;
            }
            if (address0 == FAILURE) {
                offset = index1;
                int index4 = offset;
                List<TreeNode> elements2 = new ArrayList<TreeNode>(2);
                TreeNode address5 = FAILURE;
                String chunk3 = null;
                if (offset < inputSize) {
                    chunk3 = input.substring(offset, offset + 1);
                }
                if (chunk3 != null && REGEX_7.matcher(chunk3).matches()) {
                    address5 = new TreeNode(input.substring(offset, offset + 1), offset);
                    offset = offset + 1;
                } else {
                    address5 = FAILURE;
                    if (offset > failure) {
                        failure = offset;
                        expected = new ArrayList<String>();
                    }
                    if (offset == failure) {
                        expected.add("[+-]");
                    }
                }
                if (address5 != FAILURE) {
                    elements2.add(0, address5);
                    TreeNode address6 = FAILURE;
                    int index5 = offset;
                    String chunk4 = null;
                    if (offset < inputSize) {
                        chunk4 = input.substring(offset, offset + 1);
                    }
                    if (chunk4 != null && REGEX_8.matcher(chunk4).matches()) {
                        address6 = new TreeNode(input.substring(offset, offset + 1), offset);
                        offset = offset + 1;
                    } else {
                        address6 = FAILURE;
                        if (offset > failure) {
                            failure = offset;
                            expected = new ArrayList<String>();
                        }
                        if (offset == failure) {
                            expected.add("[0-9]");
                        }
                    }
                    offset = index5;
                    if (address6 == FAILURE) {
                        address6 = new TreeNode(input.substring(offset, offset), offset);
                        offset = offset;
                    } else {
                        address6 = FAILURE;
                    }
                    if (address6 != FAILURE) {
                        elements2.add(1, address6);
                    } else {
                        elements2 = null;
                        offset = index4;
                    }
                } else {
                    elements2 = null;
                    offset = index4;
                }
                if (elements2 == null) {
                    address0 = FAILURE;
                } else {
                    address0 = new TreeNode(input.substring(index4, offset), index4, elements2);
                    offset = offset;
                }
                if (address0 == FAILURE) {
                    offset = index1;
                }
            }
            rule.put(index0, new CacheRecord(address0, offset));
        }
        return address0;
    }

    TreeNode _read_astr() {
        TreeNode address0 = FAILURE;
        int index0 = offset;
        Map<Integer, CacheRecord> rule = cache.get(Label.astr);
        if (rule == null) {
            rule = new HashMap<Integer, CacheRecord>();
            cache.put(Label.astr, rule);
        }
        if (rule.containsKey(offset)) {
            address0 = rule.get(offset).node;
            offset = rule.get(offset).tail;
        } else {
            int index1 = offset;
            List<TreeNode> elements0 = new ArrayList<TreeNode>(2);
            TreeNode address1 = FAILURE;
            address1 = _read_str();
            if (address1 != FAILURE) {
                elements0.add(0, address1);
                TreeNode address2 = FAILURE;
                int index2 = offset;
                address2 = _read_alignment();
                if (address2 == FAILURE) {
                    address2 = new TreeNode(input.substring(index2, index2), index2);
                    offset = index2;
                }
                if (address2 != FAILURE) {
                    elements0.add(1, address2);
                } else {
                    elements0 = null;
                    offset = index1;
                }
            } else {
                elements0 = null;
                offset = index1;
            }
            if (elements0 == null) {
                address0 = FAILURE;
            } else {
                address0 = actions.make_str(input, index1, offset, elements0);
                offset = offset;
            }
            rule.put(index0, new CacheRecord(address0, offset));
        }
        return address0;
    }

    TreeNode _read_str() {
        TreeNode address0 = FAILURE;
        int index0 = offset;
        Map<Integer, CacheRecord> rule = cache.get(Label.str);
        if (rule == null) {
            rule = new HashMap<Integer, CacheRecord>();
            cache.put(Label.str, rule);
        }
        if (rule.containsKey(offset)) {
            address0 = rule.get(offset).node;
            offset = rule.get(offset).tail;
        } else {
            int index1 = offset;
            List<TreeNode> elements0 = new ArrayList<TreeNode>(3);
            TreeNode address1 = FAILURE;
            String chunk0 = null;
            if (offset < inputSize) {
                chunk0 = input.substring(offset, offset + 1);
            }
            if (chunk0 != null && chunk0.equals("\"")) {
                address1 = new TreeNode(input.substring(offset, offset + 1), offset);
                offset = offset + 1;
            } else {
                address1 = FAILURE;
                if (offset > failure) {
                    failure = offset;
                    expected = new ArrayList<String>();
                }
                if (offset == failure) {
                    expected.add("\"\\\"\"");
                }
            }
            if (address1 != FAILURE) {
                elements0.add(0, address1);
                TreeNode address2 = FAILURE;
                int index2 = offset;
                int index3 = offset;
                List<TreeNode> elements1 = new ArrayList<TreeNode>(3);
                TreeNode address3 = FAILURE;
                String chunk1 = null;
                if (offset < inputSize) {
                    chunk1 = input.substring(offset, offset + 1);
                }
                if (chunk1 != null && REGEX_9.matcher(chunk1).matches()) {
                    address3 = new TreeNode(input.substring(offset, offset + 1), offset);
                    offset = offset + 1;
                } else {
                    address3 = FAILURE;
                    if (offset > failure) {
                        failure = offset;
                        expected = new ArrayList<String>();
                    }
                    if (offset == failure) {
                        expected.add("[^\\\"\\s]");
                    }
                }
                if (address3 != FAILURE) {
                    elements1.add(0, address3);
                    TreeNode address4 = FAILURE;
                    int remaining0 = 0;
                    int index4 = offset;
                    List<TreeNode> elements2 = new ArrayList<TreeNode>();
                    TreeNode address5 = new TreeNode("", -1);
                    while (address5 != FAILURE) {
                        String chunk2 = null;
                        if (offset < inputSize) {
                            chunk2 = input.substring(offset, offset + 1);
                        }
                        if (chunk2 != null && REGEX_10.matcher(chunk2).matches()) {
                            address5 = new TreeNode(input.substring(offset, offset + 1), offset);
                            offset = offset + 1;
                        } else {
                            address5 = FAILURE;
                            if (offset > failure) {
                                failure = offset;
                                expected = new ArrayList<String>();
                            }
                            if (offset == failure) {
                                expected.add("[^\\\"\\n\\r]");
                            }
                        }
                        if (address5 != FAILURE) {
                            elements2.add(address5);
                            --remaining0;
                        }
                    }
                    if (remaining0 <= 0) {
                        address4 = new TreeNode(input.substring(index4, offset), index4, elements2);
                        offset = offset;
                    } else {
                        address4 = FAILURE;
                    }
                    if (address4 != FAILURE) {
                        elements1.add(1, address4);
                        TreeNode address6 = FAILURE;
                        int index5 = offset;
                        String chunk3 = null;
                        if (offset < inputSize) {
                            chunk3 = input.substring(offset, offset + 1);
                        }
                        if (chunk3 != null && REGEX_11.matcher(chunk3).matches()) {
                            address6 = new TreeNode(input.substring(offset, offset + 1), offset);
                            offset = offset + 1;
                        } else {
                            address6 = FAILURE;
                            if (offset > failure) {
                                failure = offset;
                                expected = new ArrayList<String>();
                            }
                            if (offset == failure) {
                                expected.add("[^\\\"\\s]");
                            }
                        }
                        if (address6 == FAILURE) {
                            address6 = new TreeNode(input.substring(index5, index5), index5);
                            offset = index5;
                        }
                        if (address6 != FAILURE) {
                            elements1.add(2, address6);
                        } else {
                            elements1 = null;
                            offset = index3;
                        }
                    } else {
                        elements1 = null;
                        offset = index3;
                    }
                } else {
                    elements1 = null;
                    offset = index3;
                }
                if (elements1 == null) {
                    address2 = FAILURE;
                } else {
                    address2 = new TreeNode(input.substring(index3, offset), index3, elements1);
                    offset = offset;
                }
                if (address2 == FAILURE) {
                    address2 = new TreeNode(input.substring(index2, index2), index2);
                    offset = index2;
                }
                if (address2 != FAILURE) {
                    elements0.add(1, address2);
                    TreeNode address7 = FAILURE;
                    String chunk4 = null;
                    if (offset < inputSize) {
                        chunk4 = input.substring(offset, offset + 1);
                    }
                    if (chunk4 != null && chunk4.equals("\"")) {
                        address7 = new TreeNode(input.substring(offset, offset + 1), offset);
                        offset = offset + 1;
                    } else {
                        address7 = FAILURE;
                        if (offset > failure) {
                            failure = offset;
                            expected = new ArrayList<String>();
                        }
                        if (offset == failure) {
                            expected.add("\"\\\"\"");
                        }
                    }
                    if (address7 != FAILURE) {
                        elements0.add(2, address7);
                    } else {
                        elements0 = null;
                        offset = index1;
                    }
                } else {
                    elements0 = null;
                    offset = index1;
                }
            } else {
                elements0 = null;
                offset = index1;
            }
            if (elements0 == null) {
                address0 = FAILURE;
            } else {
                address0 = new TreeNode(input.substring(index1, offset), index1, elements0);
                offset = offset;
            }
            rule.put(index0, new CacheRecord(address0, offset));
        }
        return address0;
    }

    TreeNode _read_anum() {
        TreeNode address0 = FAILURE;
        int index0 = offset;
        Map<Integer, CacheRecord> rule = cache.get(Label.anum);
        if (rule == null) {
            rule = new HashMap<Integer, CacheRecord>();
            cache.put(Label.anum, rule);
        }
        if (rule.containsKey(offset)) {
            address0 = rule.get(offset).node;
            offset = rule.get(offset).tail;
        } else {
            int index1 = offset;
            List<TreeNode> elements0 = new ArrayList<TreeNode>(2);
            TreeNode address1 = FAILURE;
            address1 = _read_num();
            if (address1 != FAILURE) {
                elements0.add(0, address1);
                TreeNode address2 = FAILURE;
                int index2 = offset;
                address2 = _read_alignment();
                if (address2 == FAILURE) {
                    address2 = new TreeNode(input.substring(index2, index2), index2);
                    offset = index2;
                }
                if (address2 != FAILURE) {
                    elements0.add(1, address2);
                } else {
                    elements0 = null;
                    offset = index1;
                }
            } else {
                elements0 = null;
                offset = index1;
            }
            if (elements0 == null) {
                address0 = FAILURE;
            } else {
                address0 = actions.make_num(input, index1, offset, elements0);
                offset = offset;
            }
            rule.put(index0, new CacheRecord(address0, offset));
        }
        return address0;
    }

    TreeNode _read_num() {
        TreeNode address0 = FAILURE;
        int index0 = offset;
        Map<Integer, CacheRecord> rule = cache.get(Label.num);
        if (rule == null) {
            rule = new HashMap<Integer, CacheRecord>();
            cache.put(Label.num, rule);
        }
        if (rule.containsKey(offset)) {
            address0 = rule.get(offset).node;
            offset = rule.get(offset).tail;
        } else {
            int index1 = offset;
            List<TreeNode> elements0 = new ArrayList<TreeNode>(3);
            TreeNode address1 = FAILURE;
            int index2 = offset;
            String chunk0 = null;
            if (offset < inputSize) {
                chunk0 = input.substring(offset, offset + 1);
            }
            if (chunk0 != null && REGEX_12.matcher(chunk0).matches()) {
                address1 = new TreeNode(input.substring(offset, offset + 1), offset);
                offset = offset + 1;
            } else {
                address1 = FAILURE;
                if (offset > failure) {
                    failure = offset;
                    expected = new ArrayList<String>();
                }
                if (offset == failure) {
                    expected.add("[+-]");
                }
            }
            if (address1 == FAILURE) {
                address1 = new TreeNode(input.substring(index2, index2), index2);
                offset = index2;
            }
            if (address1 != FAILURE) {
                elements0.add(0, address1);
                TreeNode address2 = FAILURE;
                int remaining0 = 1;
                int index3 = offset;
                List<TreeNode> elements1 = new ArrayList<TreeNode>();
                TreeNode address3 = new TreeNode("", -1);
                while (address3 != FAILURE) {
                    String chunk1 = null;
                    if (offset < inputSize) {
                        chunk1 = input.substring(offset, offset + 1);
                    }
                    if (chunk1 != null && REGEX_13.matcher(chunk1).matches()) {
                        address3 = new TreeNode(input.substring(offset, offset + 1), offset);
                        offset = offset + 1;
                    } else {
                        address3 = FAILURE;
                        if (offset > failure) {
                            failure = offset;
                            expected = new ArrayList<String>();
                        }
                        if (offset == failure) {
                            expected.add("[0-9]");
                        }
                    }
                    if (address3 != FAILURE) {
                        elements1.add(address3);
                        --remaining0;
                    }
                }
                if (remaining0 <= 0) {
                    address2 = new TreeNode(input.substring(index3, offset), index3, elements1);
                    offset = offset;
                } else {
                    address2 = FAILURE;
                }
                if (address2 != FAILURE) {
                    elements0.add(1, address2);
                    TreeNode address4 = FAILURE;
                    int index4 = offset;
                    int index5 = offset;
                    List<TreeNode> elements2 = new ArrayList<TreeNode>(2);
                    TreeNode address5 = FAILURE;
                    String chunk2 = null;
                    if (offset < inputSize) {
                        chunk2 = input.substring(offset, offset + 1);
                    }
                    if (chunk2 != null && chunk2.equals(".")) {
                        address5 = new TreeNode(input.substring(offset, offset + 1), offset);
                        offset = offset + 1;
                    } else {
                        address5 = FAILURE;
                        if (offset > failure) {
                            failure = offset;
                            expected = new ArrayList<String>();
                        }
                        if (offset == failure) {
                            expected.add("\"\\.\"");
                        }
                    }
                    if (address5 != FAILURE) {
                        elements2.add(0, address5);
                        TreeNode address6 = FAILURE;
                        int remaining1 = 1;
                        int index6 = offset;
                        List<TreeNode> elements3 = new ArrayList<TreeNode>();
                        TreeNode address7 = new TreeNode("", -1);
                        while (address7 != FAILURE) {
                            String chunk3 = null;
                            if (offset < inputSize) {
                                chunk3 = input.substring(offset, offset + 1);
                            }
                            if (chunk3 != null && REGEX_14.matcher(chunk3).matches()) {
                                address7 = new TreeNode(input.substring(offset, offset + 1), offset);
                                offset = offset + 1;
                            } else {
                                address7 = FAILURE;
                                if (offset > failure) {
                                    failure = offset;
                                    expected = new ArrayList<String>();
                                }
                                if (offset == failure) {
                                    expected.add("[0-9]");
                                }
                            }
                            if (address7 != FAILURE) {
                                elements3.add(address7);
                                --remaining1;
                            }
                        }
                        if (remaining1 <= 0) {
                            address6 = new TreeNode(input.substring(index6, offset), index6, elements3);
                            offset = offset;
                        } else {
                            address6 = FAILURE;
                        }
                        if (address6 != FAILURE) {
                            elements2.add(1, address6);
                        } else {
                            elements2 = null;
                            offset = index5;
                        }
                    } else {
                        elements2 = null;
                        offset = index5;
                    }
                    if (elements2 == null) {
                        address4 = FAILURE;
                    } else {
                        address4 = new TreeNode(input.substring(index5, offset), index5, elements2);
                        offset = offset;
                    }
                    if (address4 == FAILURE) {
                        address4 = new TreeNode(input.substring(index4, index4), index4);
                        offset = index4;
                    }
                    if (address4 != FAILURE) {
                        elements0.add(2, address4);
                    } else {
                        elements0 = null;
                        offset = index1;
                    }
                } else {
                    elements0 = null;
                    offset = index1;
                }
            } else {
                elements0 = null;
                offset = index1;
            }
            if (elements0 == null) {
                address0 = FAILURE;
            } else {
                address0 = new TreeNode(input.substring(index1, offset), index1, elements0);
                offset = offset;
            }
            rule.put(index0, new CacheRecord(address0, offset));
        }
        return address0;
    }

    TreeNode _read_aconcept() {
        TreeNode address0 = FAILURE;
        int index0 = offset;
        Map<Integer, CacheRecord> rule = cache.get(Label.aconcept);
        if (rule == null) {
            rule = new HashMap<Integer, CacheRecord>();
            cache.put(Label.aconcept, rule);
        }
        if (rule.containsKey(offset)) {
            address0 = rule.get(offset).node;
            offset = rule.get(offset).tail;
        } else {
            int index1 = offset;
            List<TreeNode> elements0 = new ArrayList<TreeNode>(2);
            TreeNode address1 = FAILURE;
            address1 = _read_concept();
            if (address1 != FAILURE) {
                elements0.add(0, address1);
                TreeNode address2 = FAILURE;
                int index2 = offset;
                address2 = _read_alignment();
                if (address2 == FAILURE) {
                    address2 = new TreeNode(input.substring(index2, index2), index2);
                    offset = index2;
                }
                if (address2 != FAILURE) {
                    elements0.add(1, address2);
                } else {
                    elements0 = null;
                    offset = index1;
                }
            } else {
                elements0 = null;
                offset = index1;
            }
            if (elements0 == null) {
                address0 = FAILURE;
            } else {
                address0 = actions.make_concept(input, index1, offset, elements0);
                offset = offset;
            }
            rule.put(index0, new CacheRecord(address0, offset));
        }
        return address0;
    }

    TreeNode _read_concept() {
        TreeNode address0 = FAILURE;
        int index0 = offset;
        Map<Integer, CacheRecord> rule = cache.get(Label.concept);
        if (rule == null) {
            rule = new HashMap<Integer, CacheRecord>();
            cache.put(Label.concept, rule);
        }
        if (rule.containsKey(offset)) {
            address0 = rule.get(offset).node;
            offset = rule.get(offset).tail;
        } else {
            int remaining0 = 1;
            int index1 = offset;
            List<TreeNode> elements0 = new ArrayList<TreeNode>();
            TreeNode address1 = new TreeNode("", -1);
            while (address1 != FAILURE) {
                String chunk0 = null;
                if (offset < inputSize) {
                    chunk0 = input.substring(offset, offset + 1);
                }
                if (chunk0 != null && REGEX_15.matcher(chunk0).matches()) {
                    address1 = new TreeNode(input.substring(offset, offset + 1), offset);
                    offset = offset + 1;
                } else {
                    address1 = FAILURE;
                    if (offset > failure) {
                        failure = offset;
                        expected = new ArrayList<String>();
                    }
                    if (offset == failure) {
                        expected.add("[^) \\t\\n\\r]");
                    }
                }
                if (address1 != FAILURE) {
                    elements0.add(address1);
                    --remaining0;
                }
            }
            if (remaining0 <= 0) {
                address0 = new TreeNode(input.substring(index1, offset), index1, elements0);
                offset = offset;
            } else {
                address0 = FAILURE;
            }
            rule.put(index0, new CacheRecord(address0, offset));
        }
        return address0;
    }

    TreeNode _read_alignment() {
        TreeNode address0 = FAILURE;
        int index0 = offset;
        Map<Integer, CacheRecord> rule = cache.get(Label.alignment);
        if (rule == null) {
            rule = new HashMap<Integer, CacheRecord>();
            cache.put(Label.alignment, rule);
        }
        if (rule.containsKey(offset)) {
            address0 = rule.get(offset).node;
            offset = rule.get(offset).tail;
        } else {
            int index1 = offset;
            List<TreeNode> elements0 = new ArrayList<TreeNode>(2);
            TreeNode address1 = FAILURE;
            String chunk0 = null;
            if (offset < inputSize) {
                chunk0 = input.substring(offset, offset + 1);
            }
            if (chunk0 != null && chunk0.equals("~")) {
                address1 = new TreeNode(input.substring(offset, offset + 1), offset);
                offset = offset + 1;
            } else {
                address1 = FAILURE;
                if (offset > failure) {
                    failure = offset;
                    expected = new ArrayList<String>();
                }
                if (offset == failure) {
                    expected.add("\"~\"");
                }
            }
            if (address1 != FAILURE) {
                elements0.add(0, address1);
                TreeNode address2 = FAILURE;
                int remaining0 = 1;
                int index2 = offset;
                List<TreeNode> elements1 = new ArrayList<TreeNode>();
                TreeNode address3 = new TreeNode("", -1);
                while (address3 != FAILURE) {
                    String chunk1 = null;
                    if (offset < inputSize) {
                        chunk1 = input.substring(offset, offset + 1);
                    }
                    if (chunk1 != null && REGEX_16.matcher(chunk1).matches()) {
                        address3 = new TreeNode(input.substring(offset, offset + 1), offset);
                        offset = offset + 1;
                    } else {
                        address3 = FAILURE;
                        if (offset > failure) {
                            failure = offset;
                            expected = new ArrayList<String>();
                        }
                        if (offset == failure) {
                            expected.add("[A-Za-z0-9.,]");
                        }
                    }
                    if (address3 != FAILURE) {
                        elements1.add(address3);
                        --remaining0;
                    }
                }
                if (remaining0 <= 0) {
                    address2 = new TreeNode(input.substring(index2, offset), index2, elements1);
                    offset = offset;
                } else {
                    address2 = FAILURE;
                }
                if (address2 != FAILURE) {
                    elements0.add(1, address2);
                } else {
                    elements0 = null;
                    offset = index1;
                }
            } else {
                elements0 = null;
                offset = index1;
            }
            if (elements0 == null) {
                address0 = FAILURE;
            } else {
                address0 = actions.make_alignment(input, index1, offset, elements0);
                offset = offset;
            }
            rule.put(index0, new CacheRecord(address0, offset));
        }
        return address0;
    }

    TreeNode _read_s() {
        TreeNode address0 = FAILURE;
        int index0 = offset;
        Map<Integer, CacheRecord> rule = cache.get(Label.s);
        if (rule == null) {
            rule = new HashMap<Integer, CacheRecord>();
            cache.put(Label.s, rule);
        }
        if (rule.containsKey(offset)) {
            address0 = rule.get(offset).node;
            offset = rule.get(offset).tail;
        } else {
            int index1 = offset;
            int index2 = offset;
            List<TreeNode> elements0 = new ArrayList<TreeNode>(3);
            TreeNode address1 = FAILURE;
            int remaining0 = 0;
            int index3 = offset;
            List<TreeNode> elements1 = new ArrayList<TreeNode>();
            TreeNode address2 = new TreeNode("", -1);
            while (address2 != FAILURE) {
                String chunk0 = null;
                if (offset < inputSize) {
                    chunk0 = input.substring(offset, offset + 1);
                }
                if (chunk0 != null && REGEX_17.matcher(chunk0).matches()) {
                    address2 = new TreeNode(input.substring(offset, offset + 1), offset);
                    offset = offset + 1;
                } else {
                    address2 = FAILURE;
                    if (offset > failure) {
                        failure = offset;
                        expected = new ArrayList<String>();
                    }
                    if (offset == failure) {
                        expected.add("[ \\t]");
                    }
                }
                if (address2 != FAILURE) {
                    elements1.add(address2);
                    --remaining0;
                }
            }
            if (remaining0 <= 0) {
                address1 = new TreeNode(input.substring(index3, offset), index3, elements1);
                offset = offset;
            } else {
                address1 = FAILURE;
            }
            if (address1 != FAILURE) {
                elements0.add(0, address1);
                TreeNode address3 = FAILURE;
                String chunk1 = null;
                if (offset < inputSize) {
                    chunk1 = input.substring(offset, offset + 1);
                }
                if (chunk1 != null && REGEX_18.matcher(chunk1).matches()) {
                    address3 = new TreeNode(input.substring(offset, offset + 1), offset);
                    offset = offset + 1;
                } else {
                    address3 = FAILURE;
                    if (offset > failure) {
                        failure = offset;
                        expected = new ArrayList<String>();
                    }
                    if (offset == failure) {
                        expected.add("[\\n\\r]");
                    }
                }
                if (address3 != FAILURE) {
                    elements0.add(1, address3);
                    TreeNode address4 = FAILURE;
                    int remaining1 = 0;
                    int index4 = offset;
                    List<TreeNode> elements2 = new ArrayList<TreeNode>();
                    TreeNode address5 = new TreeNode("", -1);
                    while (address5 != FAILURE) {
                        String chunk2 = null;
                        if (offset < inputSize) {
                            chunk2 = input.substring(offset, offset + 1);
                        }
                        if (chunk2 != null && REGEX_19.matcher(chunk2).matches()) {
                            address5 = new TreeNode(input.substring(offset, offset + 1), offset);
                            offset = offset + 1;
                        } else {
                            address5 = FAILURE;
                            if (offset > failure) {
                                failure = offset;
                                expected = new ArrayList<String>();
                            }
                            if (offset == failure) {
                                expected.add("[ \\t]");
                            }
                        }
                        if (address5 != FAILURE) {
                            elements2.add(address5);
                            --remaining1;
                        }
                    }
                    if (remaining1 <= 0) {
                        address4 = new TreeNode(input.substring(index4, offset), index4, elements2);
                        offset = offset;
                    } else {
                        address4 = FAILURE;
                    }
                    if (address4 != FAILURE) {
                        elements0.add(2, address4);
                    } else {
                        elements0 = null;
                        offset = index2;
                    }
                } else {
                    elements0 = null;
                    offset = index2;
                }
            } else {
                elements0 = null;
                offset = index2;
            }
            if (elements0 == null) {
                address0 = FAILURE;
            } else {
                address0 = new TreeNode(input.substring(index2, offset), index2, elements0);
                offset = offset;
            }
            if (address0 == FAILURE) {
                offset = index1;
                int remaining2 = 1;
                int index5 = offset;
                List<TreeNode> elements3 = new ArrayList<TreeNode>();
                TreeNode address6 = new TreeNode("", -1);
                while (address6 != FAILURE) {
                    String chunk3 = null;
                    if (offset < inputSize) {
                        chunk3 = input.substring(offset, offset + 1);
                    }
                    if (chunk3 != null && REGEX_20.matcher(chunk3).matches()) {
                        address6 = new TreeNode(input.substring(offset, offset + 1), offset);
                        offset = offset + 1;
                    } else {
                        address6 = FAILURE;
                        if (offset > failure) {
                            failure = offset;
                            expected = new ArrayList<String>();
                        }
                        if (offset == failure) {
                            expected.add("[ \\t]");
                        }
                    }
                    if (address6 != FAILURE) {
                        elements3.add(address6);
                        --remaining2;
                    }
                }
                if (remaining2 <= 0) {
                    address0 = new TreeNode(input.substring(index5, offset), index5, elements3);
                    offset = offset;
                } else {
                    address0 = FAILURE;
                }
                if (address0 == FAILURE) {
                    offset = index1;
                }
            }
            rule.put(index0, new CacheRecord(address0, offset));
        }
        return address0;
    }

    TreeNode _read_os() {
        TreeNode address0 = FAILURE;
        int index0 = offset;
        Map<Integer, CacheRecord> rule = cache.get(Label.os);
        if (rule == null) {
            rule = new HashMap<Integer, CacheRecord>();
            cache.put(Label.os, rule);
        }
        if (rule.containsKey(offset)) {
            address0 = rule.get(offset).node;
            offset = rule.get(offset).tail;
        } else {
            int index1 = offset;
            List<TreeNode> elements0 = new ArrayList<TreeNode>(3);
            TreeNode address1 = FAILURE;
            int remaining0 = 0;
            int index2 = offset;
            List<TreeNode> elements1 = new ArrayList<TreeNode>();
            TreeNode address2 = new TreeNode("", -1);
            while (address2 != FAILURE) {
                String chunk0 = null;
                if (offset < inputSize) {
                    chunk0 = input.substring(offset, offset + 1);
                }
                if (chunk0 != null && REGEX_21.matcher(chunk0).matches()) {
                    address2 = new TreeNode(input.substring(offset, offset + 1), offset);
                    offset = offset + 1;
                } else {
                    address2 = FAILURE;
                    if (offset > failure) {
                        failure = offset;
                        expected = new ArrayList<String>();
                    }
                    if (offset == failure) {
                        expected.add("[ \\t]");
                    }
                }
                if (address2 != FAILURE) {
                    elements1.add(address2);
                    --remaining0;
                }
            }
            if (remaining0 <= 0) {
                address1 = new TreeNode(input.substring(index2, offset), index2, elements1);
                offset = offset;
            } else {
                address1 = FAILURE;
            }
            if (address1 != FAILURE) {
                elements0.add(0, address1);
                TreeNode address3 = FAILURE;
                int index3 = offset;
                String chunk1 = null;
                if (offset < inputSize) {
                    chunk1 = input.substring(offset, offset + 1);
                }
                if (chunk1 != null && REGEX_22.matcher(chunk1).matches()) {
                    address3 = new TreeNode(input.substring(offset, offset + 1), offset);
                    offset = offset + 1;
                } else {
                    address3 = FAILURE;
                    if (offset > failure) {
                        failure = offset;
                        expected = new ArrayList<String>();
                    }
                    if (offset == failure) {
                        expected.add("[\\n\\r]");
                    }
                }
                if (address3 == FAILURE) {
                    address3 = new TreeNode(input.substring(index3, index3), index3);
                    offset = index3;
                }
                if (address3 != FAILURE) {
                    elements0.add(1, address3);
                    TreeNode address4 = FAILURE;
                    int remaining1 = 0;
                    int index4 = offset;
                    List<TreeNode> elements2 = new ArrayList<TreeNode>();
                    TreeNode address5 = new TreeNode("", -1);
                    while (address5 != FAILURE) {
                        String chunk2 = null;
                        if (offset < inputSize) {
                            chunk2 = input.substring(offset, offset + 1);
                        }
                        if (chunk2 != null && REGEX_23.matcher(chunk2).matches()) {
                            address5 = new TreeNode(input.substring(offset, offset + 1), offset);
                            offset = offset + 1;
                        } else {
                            address5 = FAILURE;
                            if (offset > failure) {
                                failure = offset;
                                expected = new ArrayList<String>();
                            }
                            if (offset == failure) {
                                expected.add("[ \\t]");
                            }
                        }
                        if (address5 != FAILURE) {
                            elements2.add(address5);
                            --remaining1;
                        }
                    }
                    if (remaining1 <= 0) {
                        address4 = new TreeNode(input.substring(index4, offset), index4, elements2);
                        offset = offset;
                    } else {
                        address4 = FAILURE;
                    }
                    if (address4 != FAILURE) {
                        elements0.add(2, address4);
                    } else {
                        elements0 = null;
                        offset = index1;
                    }
                } else {
                    elements0 = null;
                    offset = index1;
                }
            } else {
                elements0 = null;
                offset = index1;
            }
            if (elements0 == null) {
                address0 = FAILURE;
            } else {
                address0 = new TreeNode(input.substring(index1, offset), index1, elements0);
                offset = offset;
            }
            rule.put(index0, new CacheRecord(address0, offset));
        }
        return address0;
    }
}
