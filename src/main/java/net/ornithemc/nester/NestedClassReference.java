package net.ornithemc.nester;

public class NestedClassReference
{
    /** The inner class' full name */
    public final String className;
    /** The enclosing class' full name */
    public final String enclosingClassName;
    /** The enclosing method's name */
    public final String enclosingMethodName;
    /** The enclosing method's descriptor */
    public final String enclosingMethodDesc;
    /** The simple name of the inner class */
    public final String innerName;
    /** The access flags of the inner class */
    public final int accessFlags;

    public NestedClassReference(String className, String enclosingClassName, String enclosingMethodName, String enclosingMethodDesc, String innerName, int accessFlags) {
        this.className = className;
        this.enclosingClassName = enclosingClassName;
        this.enclosingMethodName = enclosingMethodName;
        this.enclosingMethodDesc = enclosingMethodDesc;
        this.innerName = innerName;
        this.accessFlags = accessFlags;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof NestedClassReference) {
            NestedClassReference ref = (NestedClassReference) obj;
            return className.equals(ref.className);
        }

        return false;
    }
}
