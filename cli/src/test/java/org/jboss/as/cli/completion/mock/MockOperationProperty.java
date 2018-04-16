package org.jboss.as.cli.completion.mock;

/**
 * Created by Marek Marusic <mmarusic@redhat.com> on 3/2/18.
 */
public class MockOperationProperty {
    private String name;
    private String[] possibleValues;
    private boolean valueRequired;

    public MockOperationProperty(String name) {
        this(name, null, true);
    }

    public MockOperationProperty(String name, String[] possibleValues) {
        this(name, possibleValues, true);
    }

    public MockOperationProperty(String name, String[] possibleValues, boolean valueRequired) {
        this.name = name;
        this.possibleValues = possibleValues;
        this.valueRequired = valueRequired;
    }

    public String[] getPossibleValues() {
        return possibleValues;
    }

    public void setPossibleValues(String[] possibleValues) {
        this.possibleValues = possibleValues;
    }

    public boolean isValueRequired() {
        return valueRequired;
    }

    public void setValueRequired(boolean valueRequired) {
        this.valueRequired = valueRequired;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
