/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package studio.driver.model;

public class TransferStatus {

    private boolean done;
    private int transferred;
    private int total;
    private double speed;

    public TransferStatus() {
    }

    public TransferStatus(boolean done, int transferred, int total, double speed) {
        this.done = done;
        this.transferred = transferred;
        this.total = total;
        this.speed = speed;
    }

    public boolean isDone() {
        return done;
    }

    public void setDone(boolean done) {
        this.done = done;
    }

    public int getTransferred() {
        return transferred;
    }

    public void setTransferred(int transferred) {
        this.transferred = transferred;
    }

    public int getTotal() {
        return total;
    }

    public void setTotal(int total) {
        this.total = total;
    }

    public double getSpeed() {
        return speed;
    }

    public void setSpeed(double speed) {
        this.speed = speed;
    }

    @Override
    public String toString() {
        return "TransferStatus{" +
                "done=" + done +
                ", transferred=" + transferred +
                ", total=" + total +
                ", speed=" + speed +
                '}';
    }
}
