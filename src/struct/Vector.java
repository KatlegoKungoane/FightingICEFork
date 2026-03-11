package struct;

public class Vector {
    public float x;
    public float y;

    public Vector(float x, float y) {
        this.x = x;
        this.y = y;
    }

    public Vector(Vector vector){
        this.x = vector.x;
        this.y = vector.y;
    }
}