/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package controller;

/**
 *
 * @author CZC
 */
public interface StateListener {

    public void transmitProgressEvent(double percent);

    public void systemInforEvent(String message);

    public void stopCommandReceived();

}
