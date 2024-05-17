/*
 * https://PowerNukkit.org - The Nukkit you know but Powerful!
 * Copyright (C) 2020  José Roberto de Araújo Júnior
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package cn.nukkit.math;

import java.util.Objects;

/**
 * @author joserobjr
 * @since 2020-09-20
 */
public class ChunkVector2 {
    private int x;
    private int z;

    public ChunkVector2() {
        this(0, 0);
    }

    public ChunkVector2(int x) {
        this(x, 0);
    }

    public ChunkVector2(int x, int z) {
        this.x = x;
        this.z = z;
    }

    public int getX() {
        return this.x;
    }

    public void setX(int x) {
        this.x = x;
    }

    public int getZ() {
        return this.z;
    }

    public void setZ(int z) {
        this.z = z;
    }

    public ChunkVector2 add(int x) {
        return this.add(x, 0);
    }

    public ChunkVector2 add(int x, int y) {
        return new ChunkVector2(this.x + x, this.z + y);
    }

    public ChunkVector2 add(ChunkVector2 x) {
        return this.add(x.getX(), x.getZ());
    }

    public ChunkVector2 subtract(int x) {
        return this.subtract(x, 0);
    }

    public ChunkVector2 subtract(int x, int y) {
        return this.add(-x, -y);
    }

    public ChunkVector2 subtract(ChunkVector2 x) {
        return this.add(-x.getX(), -x.getZ());
    }

    public ChunkVector2 abs() {
        return new ChunkVector2(Math.abs(this.x), Math.abs(this.z));
    }

    public ChunkVector2 multiply(int number) {
        return new ChunkVector2(this.x * number, this.z * number);
    }

    public ChunkVector2 divide(int number) {
        return new ChunkVector2(this.x / number, this.z / number);
    }

    public double distance(double x) {
        return this.distance(x, 0);
    }

    public double distance(double x, double y) {
        return Math.sqrt(this.distanceSquared(x, y));
    }

    public double distance(ChunkVector2 vector) {
        return Math.sqrt(this.distanceSquared(vector.getX(), vector.getZ()));
    }

    public double distanceSquared(double x) {
        return this.distanceSquared(x, 0);
    }

    public double distanceSquared(double x, double y) {
        return Math.pow(this.x - x, 2) + Math.pow(this.z - y, 2);
    }

    public double distanceSquared(ChunkVector2 vector) {
        return this.distanceSquared(vector.getX(), vector.getZ());
    }

    public double length() {
        return Math.sqrt(this.lengthSquared());
    }

    public int lengthSquared() {
        return this.x * this.x + this.z * this.z;
    }

    public int dot(ChunkVector2 v) {
        return this.x * v.x + this.z * v.z;
    }

    @Override
    public String toString() {
        return "MutableChunkVector(x=" + this.x + ",z=" + this.z + ")";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        ChunkVector2 that = (ChunkVector2) o;
        return x == that.x && z == that.z;
    }

    @Override
    public int hashCode() {
        return Objects.hash(x, z);
    }
}
