package com.lkrasner.usbserial;

import com.lkrasner.arduinocontrol.R;

import android.app.Fragment;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

public class MyFragmentA extends Fragment {

 @Override
 public View onCreateView(LayoutInflater inflater, ViewGroup container,
   Bundle savedInstanceState) {
  View myFragmentView = inflater.inflate(R.layout.main, container, false);
  return myFragmentView;
 }

}