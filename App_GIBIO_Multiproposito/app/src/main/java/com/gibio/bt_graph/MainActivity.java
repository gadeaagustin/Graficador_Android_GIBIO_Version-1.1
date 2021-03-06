package com.gibio.bt_graph;

import android.app.Activity;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.graphics.Color;
import android.media.MediaScannerConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Toast;
import android.widget.ToggleButton;

import com.jjoe64.graphview.GraphView;
import com.jjoe64.graphview.GraphView.GraphViewData;
import com.jjoe64.graphview.GraphView.LegendAlign;
import com.jjoe64.graphview.GraphViewSeries;
import com.jjoe64.graphview.GraphViewSeries.GraphViewStyle;
import com.jjoe64.graphview.LineGraphView;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

/*TODO: [LL]: Probar el funcionamiento con el fisio; Verificar funcionamiento de la grabación  en formato CSV */

public class MainActivity extends Activity implements View.OnClickListener {
	static int Sereies_0_index = 0, Sereies_1_index = 0;
	static boolean Record_Dialog_On;
	static String DataWrite = new String("");
	//static byte[] DataWrite = new byte[1024 * 1000];
	//static int[] DataToWrite = new int[1024 * 1000];
	static int j = 0;
	static int data_aux[]= new int[4];
	static int cont=0, canal_ant=-1;
	static double data_channel_0_prev=0, data_channel_1_prev=0;


	public static final int MIN_Y_Grap_0 = -3000000, MIN_Y_Grap_1 = -3000000, MAX_Y_Grap_0 = 3000000, MAX_Y_Grap_1 = 3000000;
	SimpleFileDialog FileSaveDialog;

	@Override
	public void onBackPressed() {
		// TODO Auto-generated method stub
		if (Bluetooth.connectedThread != null) {
			Bluetooth.connectedThread.write("0");
		}//Stop streaming
		super.onBackPressed();
	}

	//toggle Button
	static boolean Lock;//whether lock the x-axis to 0-5
	static boolean AutoScrollX;//auto scroll to the last x value
	static boolean Stream;//Start or stop streaming
	static boolean Record; //Enable Data recording when set to true
	//Button init
	Button bXminus;
	Button bXplus;
	ToggleButton tbLock;
	ToggleButton tbScroll;
	ToggleButton tbStream;
	ToggleButton tbRecord;
	//GraphView init
	static LinearLayout GraphView_0, GraphView_1;
	static GraphView graphView_0, graphView_1;
	static GraphViewSeries Series_0, Series_1;
	//graph value
	private static double graph2LastXValue_0 = 0, graph2LastXValue_1 = 0;
	private static int Xview = 60;
	Button bConnect, bDisconnect;

	private Handler mHandler = new Handler() {
		boolean recording = false;

		@Override
		public void handleMessage(Message msg) {
			// TODO Auto-generated method stub
			int channel;
			double data;
			super.handleMessage(msg);
			switch (msg.what) {
				case Bluetooth.SUCCESS_CONNECT:
					Bluetooth.connectedThread = new Bluetooth.ConnectedThread((BluetoothSocket) msg.obj);
					Toast.makeText(getApplicationContext(), "Conectado!", 0).show();
					String s = "successfully connected";
					Bluetooth.connectedThread.start();
					break;
				case Bluetooth.MESSAGE_READ:
					if (!Record_Dialog_On) {
						byte[] readBuf = (byte[]) msg.obj;
						for (int i = 0; i < readBuf.length + cont ; i++) {  //[LL]: Barrido del buffer de 1024 byte
						//	Log.d("Cont.", Integer.toString(cont));
							if (cont==3) {
								data_aux[3]= (int) readBuf[i]  & 0xff;
								cont=0;

							if ((data_aux[0] & 0x80) == 0x80 && (data_aux[1] & 0x80) == 0 && (data_aux[2] & 0x80) == 0 && (data_aux[3] & 0x80) == 0) {
								data = ProcessData(data_aux); //Obtengo el valor a graficar segun el formato de los datos
								channel = (data_aux[0] & 0x38) >> 3; //Obtengo el canal a graficar. Mascara= 0x38= 111000b
								//i = i + 3;
								if (Record == true && j < (1024 - 4)) {
									recording = true;
									if(canal_ant==-1)
									{
										if(channel==0)
											canal_ant= 1;
										else if(channel==1) {
											canal_ant = 0;
											DataWrite = DataWrite.concat(Double.toString(data_channel_0_prev) + ",");
										}
									}
									switch(canal_ant) {
										case 0:
											if(channel==1) {
												DataWrite = DataWrite.concat(Double.toString(data) + "\n");
												canal_ant=1;
												data_channel_1_prev= data;
											}
											else if(channel==0) {
												DataWrite = DataWrite.concat(Double.toString(data_channel_1_prev) + "\n" + data + ",");
												data_channel_0_prev = data;
											}
											break;
										case 1:
											if(channel==0) {
												DataWrite = DataWrite.concat(Double.toString(data) + ",");
												canal_ant = 0;
												//DataWrite = DataWrite.concat(Double.toString(data_channel_1_prev) + "\n" + data + ",");
												data_channel_0_prev = data;
											}
											else if(channel==1) {
												DataWrite = DataWrite.concat(Double.toString(data_channel_0_prev) + "," + data + "\n");
												//DataWrite = DataWrite.concat(Double.toString(data_channel_1_prev) + "\n" + data + ",");
												data_channel_1_prev = data;
											}
											break;
										default:
											break;
									}

									j++;

								} else if (recording == true) {
									Log.d("com.gibio.bt_graph", "grabando...");
									String DirToSaveType;
									if (FileSaveDialog.Get_m_dir().equals("") == false)
										DirToSaveType = FileSaveDialog.Get_m_dir();
									else DirToSaveType = FileSaveDialog.Get_m_sdcardDirectory();
									SaveData(DataWrite, DirToSaveType, FileSaveDialog.Get_Selected_File_Name()); //Guardo los datos en un archivo
									DataWrite = (String) "";
									//canal_ant = -1; // -1 == ningún canal

									if (Record == false) {    //[LL]: si se presionó STOP dejo de grabar
										recording = false;
										Toast.makeText(getApplicationContext(), "Grabación detenida!", 0).show();j = 0;
									}
									j = 0;
								}

								switch (channel) {
									case 0:
										if (Sereies_0_index < 10000){ //[LL]:Esto lo puse para evitar picos en la memoria
											if(data!=-2097152.0 && data!=0.0 && data!=409600.0 && data!=393216.0) { //[LL]:Por un error (picos) ver como resolver
										//Notar que todos los valores que dan error son multiplos de 1024
										//393216=    0x060000 = 0000 0110 00000000 00000000
										//409600=    0x064000 = 0000 0110 01000000 00000000
										//con el protocolo: 10001000 00110010 00000000 00000000

										// -2097152= 0xE00000 = 1110 0000 00000000 00000000

										Series_0.appendData(new GraphViewData(graph2LastXValue_0, data), AutoScrollX);
										Sereies_0_index++;
												}
										} else {
											Series_0.resetData(new GraphViewData[]{});
											Sereies_0_index = 0;
										}
										//X-axis control
										if (graph2LastXValue_0 >= Xview && Lock == true) {
											Series_0.resetData(new GraphViewData[]{});
											graph2LastXValue_0 = 0;
										} else graph2LastXValue_0 += 0.1;

										if (Lock == true) {
											graphView_0.setViewPort(0, Xview);
										} else {
											graphView_0.setViewPort(graph2LastXValue_0 - Xview, Xview);
										}
										//refresh
										GraphView_0.removeView(graphView_0);
										GraphView_0.addView(graphView_0);
										break;

									case 1:
										if (Sereies_1_index < 10000) {
										if(data!=-2097152.0 && data!=0.0 && data!=409600.0 && data!=393216.0 ) {
										//if(data!=-2097152.0 ) {
											Series_1.appendData(new GraphViewData(graph2LastXValue_1, data), AutoScrollX);
											//Log.d("com.gibio.bt_graph", "data:" + Double.toString(data));
											Sereies_1_index++;
													}
										} else {
											Series_1.resetData(new GraphViewData[]{});
											Sereies_1_index = 0;
										}
										//X-axis control
										if (graph2LastXValue_1 >= Xview && Lock == true) {
											Series_1.resetData(new GraphViewData[]{});
											graph2LastXValue_1 = 0;
										} else graph2LastXValue_1 += 0.1;

										if (Lock == true) {
											graphView_1.setViewPort(0, Xview);
										} else {
											graphView_1.setViewPort(graph2LastXValue_1 - Xview, Xview);
										}
										//refresh
										GraphView_1.removeView(graphView_1);
										GraphView_1.addView(graphView_1);
										break;
								}
							}
						}
						else {
								data_aux[cont] = (int) readBuf[i] & 0xff;
								cont++;
							}
						}
					}

					break;
				default:
					break;

			}
		}

		public boolean isFloatNumber(String num) {
			//Log.d("checkfloatNum", num);
			try {
				Double.parseDouble(num);
			} catch (NumberFormatException nfe) {
				return false;
			}
			return true;
		}
	};

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		this.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
		requestWindowFeature(Window.FEATURE_NO_TITLE);//Hide title
		this.getWindow().setFlags(WindowManager.LayoutParams.
				FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);//Hide Status bar
		setContentView(R.layout.activity_main);
		//set background color
		LinearLayout background = (LinearLayout) findViewById(R.id.bg);
		background.setBackgroundColor(Color.BLACK);
		//background.setBackgroundColor(Color.WHITE);
		init();
		ButtonInit();
	}

	void init() {
		Bluetooth.gethandler(mHandler);

		//init graphview
		GraphView_0 = (LinearLayout) findViewById(R.id.Graph_0);
		// init example series data------------------- 
		Series_0 = new GraphViewSeries("EEG",
				new GraphViewStyle(Color.YELLOW, 2),//color and thickness of the line 
				new GraphViewData[]{new GraphViewData(0, 0)});
		graphView_0 = new LineGraphView(
				this // context  
				, "EEG" // heading
		);
		graphView_0.setViewPort(0, Xview);
		graphView_0.setScrollable(true);
		graphView_0.setScalable(true);
		graphView_0.setShowLegend(true);
		graphView_0.setLegendAlign(LegendAlign.BOTTOM);
		//graphView_0.setManualYAxis(true);
		//graphView_0.setManualYAxisBounds(10000, 0);
		//graphView_0.setManualYAxisBounds(MAX_Y_Grap_0, MIN_Y_Grap_0);
		graphView_0.setBackgroundColor(Color.argb(180, 255,255, 255));
		graphView_0.addSeries(Series_0); // data
		GraphView_0.addView(graphView_0);


		//[LL]
		//init graphview
		GraphView_1 = (LinearLayout) findViewById(R.id.Graph_1);
		// init example series data-------------------
		Series_1 = new GraphViewSeries("EOG",
				new GraphViewStyle(Color.RED, 2),//color and thickness of the line
				new GraphViewData[]{new GraphViewData(0, 0)});
		graphView_1 = new LineGraphView(
				this // context
				, "EOG" // heading
		);
		graphView_1.setViewPort(0, Xview);
		graphView_1.setScrollable(true);
		graphView_1.setScalable(true);
		graphView_1.setShowLegend(true);
		graphView_1.setLegendAlign(LegendAlign.BOTTOM);
		//graphView_1.setManualYAxis(true);
		//graphView_1.setManualYAxisBounds(MAX_Y_Grap_1, MIN_Y_Grap_1);
		graphView_1.setBackgroundColor(Color.argb(180, 255,255, 255));
		graphView_1.addSeries(Series_1); // data
		GraphView_1.addView(graphView_1);

	}

	void ButtonInit() {
		bConnect = (Button) findViewById(R.id.bConnect);
		bConnect.setOnClickListener(this);
		bDisconnect = (Button) findViewById(R.id.bDisconnect);
		bDisconnect.setOnClickListener(this);
		//X-axis control button
		bXminus = (Button) findViewById(R.id.bXminus);
		bXminus.setOnClickListener(this);
		bXplus = (Button) findViewById(R.id.bXplus);
		bXplus.setOnClickListener(this);
		//
		tbLock = (ToggleButton) findViewById(R.id.tbLock);
		tbLock.setOnClickListener(this);
		tbScroll = (ToggleButton) findViewById(R.id.tbScroll);
		tbScroll.setOnClickListener(this);
		tbStream = (ToggleButton) findViewById(R.id.tbStream);
		tbStream.setOnClickListener(this);
		//Record Button
		tbRecord = (ToggleButton) findViewById(R.id.tbRecord);
		tbRecord.setOnClickListener(new OnClickListener() {
			String m_chosen;

			@Override
			public void onClick(View v) {

				if (tbRecord.isChecked()) {
					Record_Dialog_On = true;
					Record = true;
					/////////////////////////////////////////////////////////////////////////////////////////////////
					//Create FileOpenDialog and register a callback
					/////////////////////////////////////////////////////////////////////////////////////////////////
					FileSaveDialog = new SimpleFileDialog(MainActivity.this, "FileSave",
							new SimpleFileDialog.SimpleFileDialogListener() {
								@Override
								public void onChosenDir(String chosenDir) {
									// The code in this function will be executed when the Record button is pushed
									m_chosen = chosenDir;
									Toast.makeText(MainActivity.this, "Chosen FileOpenDialog File: " +
											m_chosen, Toast.LENGTH_LONG).show();
									Record_Dialog_On = false;
								}
							});

					//You can change the default filename using the public variable "Default_File_Name"
					FileSaveDialog.Default_File_Name = "Datos.txt";
					FileSaveDialog.chooseFile_or_Dir();

					/////////////////////////////////////////////////////////////////////////////////////////////////
				} else {
					Record = false;
				}

			}
		});


		//init toggleButton
		Lock = false;
		AutoScrollX = true;
		Stream = true;
		Record = false;
	}

	@Override
	public void onClick(View v) {
		// TODO Auto-generated method stub
		switch (v.getId()) {
			case R.id.bConnect:
				startActivity(new Intent("android.intent.action.BT1"));
				break;
			case R.id.bDisconnect:
				Bluetooth.disconnect();
				break;
			case R.id.bXminus:
				if (Xview > 1) Xview--;
				break;
			case R.id.bXplus:
				if (Xview < 100) Xview++;
				break;
			case R.id.tbLock:
				if (tbLock.isChecked()) {
					Lock = true;
				} else {
					Lock = false;
				}
				break;
			case R.id.tbScroll:
				if (tbScroll.isChecked()) {
					AutoScrollX = true;
				} else {
					AutoScrollX = false;
				}
				break;
			case R.id.tbStream:
				if (tbStream.isChecked()) {
					if (Bluetooth.connectedThread != null)
						Bluetooth.connectedThread.write(Character.toString((char) 5));
				} else {
					if (Bluetooth.connectedThread != null)
						Bluetooth.connectedThread.write("0");
				}
				break;
		/*
		case R.id.tbRecord:
			if (tbRecord.isChecked()){
				Record = true;

			}else{
				Record = false;
			}
			break;
	*/
		}
	}

    /*
	* ProcessData recibe un array int[] con los 4 bytes del paquete y obtiene de él el valor de 24 bits implicito en el paquete
	*Protocolo de recepción para el Fisioremoto:
	* Formato de los Datos, son 4 bytes:  1,X,CH2,CH1,CH0,b23,b22,b21 | 0,b20,b19,b18,b17,b16,b15,b14 | 0,b13,b12,b11,b10,b9,b8,b7 | 0,b6,b5,b4,b3,b2,b1,b0
	**/
	double ProcessData(int[] data)
	{
		//data[0] parte alta alta
		//data[1] parte alta
		//data[2] parte media
		//data[3] parte baja

		int aux = 0;

		aux = (int)((data[2] << 7) & 0xff);
		data[3] = (int)((data[3] | aux) & 0xff);
		data[2] = (int)((data[2] >> 1) & 0xff);

		aux = (int)((data[1] << 6) & 0xff);
		data[2] = (int)((data[2] | aux) & 0xff);
		data[1] = (int)((data[1] >> 2) & 0xff);

		aux = (int)((data[0] << 5) & 0xff);
		data[1] = (int)(data[1] | aux);

		int neg=0x00;
		if ((data[1] & 0x80) == 0x80)
			neg = 0xFF;
		int dato = (neg << 24) + (data[1] << 16) + (data[2] << 8) + (data[3]);

		//return Convert.ToDouble(dato) / Math.Pow(2, 23) * SCALE;
        return (double)dato;
	}

	//Guardar datos en un archivo:
void SaveData(String data, String Directory, String FileName) {
	try {
		// Creates a trace file in the primary external storage space of the
		// current application.
		// If the file does not exists, it is created.
		//File traceFile = new File(((Context) this).getExternalFilesDir(null), FileName);
		File traceFile = new File(Directory, FileName);
		if (!traceFile.exists())
			traceFile.createNewFile();
		// Adds a line to the trace file
		BufferedWriter writer = new BufferedWriter(new FileWriter(traceFile, true /*append*/));
		writer.write(data);
		writer.close();
		// Refresh the data so it can seen when the device is plugged in a
		// computer. You may have to unplug and replug the device to see the
		// latest changes. This is not necessary if the user should not modify
		// the files.
		MediaScannerConnection.scanFile((Context) (this),
				new String[]{traceFile.toString()},
				null,
				null);

	} catch (IOException e) {
		Log.e("com.gibio.bt_graph", "Unable to write to the TraceFile.txt file.");
	}
}

}
