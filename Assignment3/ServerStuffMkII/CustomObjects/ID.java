package ServerStuffMkII.CustomObjects;

import java.util.Arrays;

public class ID {
    private final byte[] key;

    public ID() {this.key = generateKey();}
    public ID(byte[] key) {this.key = key;}

    public String toString() {return Arrays.toString(this.key);}
    public byte[] toBytes() {return this.key;}
    public void printKey() {System.out.println(Arrays.toString(key));}

    // ------------------------------------ This is needed for hashmap to use ID object as KEY ---------------------- //
    // Equal value will produce equal hashcode.
    @Override
    public int hashCode() {
        int a = 0;
        for (byte value : key) {
            a += value;
        }
        return a;
    }

    // Equal value will consider equal as object.
    @Override
    public boolean equals(Object object) {
        if (this == object) {return true;}   // Same memory address
        if (object == null) {return false;}
        if (getClass() != object.getClass()) {return false;}

        ID other = (ID) object;
        byte[] otherKey = other.toBytes();
        if (this.key.length == otherKey.length) {
            for (int a = 0; a < this.key.length; a++) {
                if (this.key[a] != otherKey[a]) {
                    return false;
                }
            }
            return true;
        } else {
            return false;
        }
    }
    // -------------------------------------------------------------------------------------------------------------- //

    private byte[] generateKey() {
        byte[] key = new byte[8];
        for (int i = 0; i < 8; i++) {
            key[i] = (byte) (Math.random() * (127 + 128) - 128);
        }
        return key;
    }
}
