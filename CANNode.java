import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.text.DecimalFormat;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Random;
import java.util.Scanner;

public class CANNode {
	
	static String myIpaddress = null;
	public String bootStrapServerIP = null;
	private String myVID = "999";
	private Map<String, String> myNeighbours = new HashMap<>();
	
	ServerSocket serverSoc = null;
	
	public CANNode(int port) {
		try {
			serverSoc = new ServerSocket(port);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	public synchronized Map<String, String> getMyNeighbours() {
		return myNeighbours;
	}

	public synchronized void setMyNeighbours(Map<String, String> myNeighbours) {
		this.myNeighbours = myNeighbours;
	}
	
	public synchronized String getMyVID() {
		return myVID;
	}

	public synchronized void setMyVID(String myVID) {
		this.myVID = myVID;
	}
	
	@SuppressWarnings("resource")
	public static void main(String[] args) {
		CANNode canNode = new CANNode(8001);
		Scanner scanner = new Scanner(System.in);
		InetAddress inetAddress;
		try {
			inetAddress = InetAddress.getLocalHost();
			myIpaddress = inetAddress.getHostAddress();
			System.out.println("My IP Address :"+myIpaddress);
		} catch (UnknownHostException e) {
			e.printStackTrace();
		}
		//myIpaddress = args[0];
		if(args.length != 1){
			System.out.println("Input Foramt :");
			System.out.println("java CANNode ipaddress");
			System.out.println("ipaadress --> BootStrap Server");
			System.exit(0);
		}

		canNode.bootStrapServerIP = args[0];
		canNode.join();
		new Deamon(canNode, canNode.serverSoc).start();
		while(true){
			System.out.println("1:Peer Information	2:Leave");
			int option = Integer.parseInt(scanner.nextLine());
			switch(option){
				/*case 1: System.out.println("Enter File name to insert into CAN");
						String fileName = scanner.nextLine();
						canNode.TranferFileToCAN(fileName);
						break;
				case 2:	//call search file
						break;*/
				case 1: canNode.DisplayPeerInformation();
						break;
				case 2:	canNode.leave();
						System.exit(0);
						break;
				default: System.out.println("Invalid Option");
				        break;
			}
		}
	}

	
	private void DisplayPeerInformation() {
		System.out.println("Peer IP address                 Peer VID");
		System.out.println("----------------------------------------");
		for(Map.Entry<String, String> entry : 
			getMyNeighbours().entrySet()){
			System.out.println(entry.getKey()+"                      "+entry.getValue());
		}
		System.out.println("");
	}

	private void leave() {
		Socket socket;
		String bootStrapServermsg;
		try {
			socket = new Socket(bootStrapServerIP, 8000);
			BufferedReader in = new BufferedReader
					(new InputStreamReader (socket.getInputStream()));
			PrintWriter out = new PrintWriter(socket.getOutputStream(),true);
			
			if(this.getMyVID().equals("999"))
				updateBootStrapServer(out,2);
			else{
				int min = 0;
				Map.Entry<String, String> takeOverNode = null;
				for(Map.Entry<String, String> entry : 
					getMyNeighbours().entrySet()){
					if(isMySibling(entry.getValue())){
						removeMyVIDFromNeighbours();
						transferControlToSibling(entry.getKey());
						takeOverNode = null;
						break;
					}
					else if(min<Integer.parseInt(entry.getValue(), 2)){
						min = Integer.parseInt(entry.getValue(), 2);
						takeOverNode = entry;
					}
				}
				if(takeOverNode != null){
					removeMyVIDFromNeighbours();
					transferControlToTakeOverNode(takeOverNode.getKey());
				}
				updateBootStrapServer(out,2);
			}
			in.readLine();
			bootStrapServermsg = in.readLine();
			System.out.println(bootStrapServermsg);
			in.close();
			out.close();
			socket.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}


	private void transferControlToTakeOverNode(String takeOverNodeIP) {
		try {
			Socket socket = new Socket(takeOverNodeIP, 8001);
			ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
			out.writeObject(Requests.TAKE_OVER);
			out.writeObject(myVID);
			out.writeObject(myNeighbours);
			out.close();
			socket.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public void transferControlToSibling(String SiblingIP) {
		try {
			Socket socket = new Socket(SiblingIP, 8001);
			ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
			out.writeObject(Requests.TRANFER_CONTROL);
			out.writeObject(myNeighbours);
			out.close();
			socket.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
		
	}

	public void removeMyVIDFromNeighbours() {
		for(Map.Entry<String, String> entry : 
			getMyNeighbours().entrySet()){
			try {
				Socket socket = new Socket(entry.getKey(), 8001);
				ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
				out.writeObject(Requests.REMOVE_VID);
				out.writeObject(CANNode.myIpaddress);
				out.close();
				socket.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}
	
	public boolean isMySibling(String neighbourVid) {
		if(myVID.length() != neighbourVid.length())
			return false;
		else{
			for(int i=0;i<neighbourVid.length()-1;i++)
				if(myVID.charAt(i) != neighbourVid.charAt(i))
					return false;
		}
		
		return true;	
	}

	private void TranferFileToCAN(String fileName) {
		File file = new File(fileName);
		Point point = computeRandomPoint(fileName);
		try {
			BufferedReader br = new BufferedReader(new FileReader(file));
			String line;
			while((line = br.readLine()) != null){
				System.out.println(line);
			}
			br.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private void join() {
		String CanNodeipaddress = null;
		String bootStrapServermsg;
		try {
			Socket socket = new Socket(bootStrapServerIP, 8000);
			BufferedReader in = new BufferedReader
					(new InputStreamReader (socket.getInputStream()));
			PrintWriter out = new PrintWriter(socket.getOutputStream(),true);
			 
			CanNodeipaddress = in.readLine();
			//System.out.println("Random CAN system ipadress :"+CanNodeipaddress);
			
			if(CanNodeipaddress.equals("999"))
				updateBootStrapServer(out,1);
			else{
				startRounting(CanNodeipaddress);
				updateBootStrapServer(out,1);
			}
			bootStrapServermsg = in.readLine();
			System.out.println(bootStrapServermsg);
			in.close();
			out.close();
			socket.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}


	@SuppressWarnings("unchecked")
	private void startRounting(String ipaddress) {
		Point pointInCanSystem = computeRandomPoint(myIpaddress);
		//System.out.println("My Point to join :"+pointInCanSystem.getX()+" "+pointInCanSystem.getY());
		try{
			 Socket socket = new Socket(ipaddress, 8001);
			 ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
			 out.writeObject(Requests.JOIN);
			 out.writeObject(myIpaddress);
			 out.writeObject(pointInCanSystem);
			 out.close();
			 socket.close();
			 
			 Socket CANResponse = serverSoc.accept(); 
	         ObjectInputStream in = new ObjectInputStream(CANResponse.getInputStream());
	         setMyVID((String) in.readObject());
	         setMyNeighbours((HashMap<String, String>)in.readObject());
	         in.close();
	         CANResponse.close();
	         
	         updateMyNeighbours(this.myVID);
	         
	         //System.out.println("New Joiner neighbours :");
	         //System.out.println(getMyNeighbours());
	         
		}catch (IOException | ClassNotFoundException e) {
			e.printStackTrace();
		}

	}

	public void updateMyNeighbours(String myVID) {
		for(Map.Entry<String, String> entry : 
			getMyNeighbours().entrySet()){
			try {
				Socket socket = new Socket(entry.getKey(), 8001);
				ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
				out.writeObject(Requests.UPDATE_VID);
				out.writeObject(CANNode.myIpaddress);
				out.writeObject(this.myVID);
				out.close();
				socket.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	private Point computeRandomPoint(String str) {
		Point p = new Point();
		Random r = new Random();
		DecimalFormat f = new DecimalFormat("0.00");
		double x = Double.parseDouble(f.format(10 * r.nextDouble()));
		double y = Double.parseDouble(f.format(10 * r.nextDouble()));
		p.setX(x);
		p.setY(y);
		return p;	
	}

	private void updateBootStrapServer(PrintWriter out, int signal) {
		/*if(signal == 1)
			System.out.println("Adding my ipaddress: "+myIpaddress+" "
					+ "to BootStrap Server!!");
		else
			System.out.println("removing my ipaddress: "+myIpaddress+" "
					+ "from BootStrap Server!!");*/
		out.println(signal);
		out.println(myIpaddress);
	}

}


class Deamon extends Thread{
	CANNode node;
	ServerSocket serverSoc;
	public Deamon(CANNode node, ServerSocket serverSoc) {
		this.node = node;
		this.serverSoc = serverSoc;
	}
	
	@Override
	public void run() {
		while(true){
			Socket clientSoc;
			try {
				//System.out.println("Will serve the requests");
				clientSoc = serverSoc.accept();
				new Worker(clientSoc, node).start();
			} catch (IOException e) {
				System.out.println("Joined but cannot serve the requests:"+e.getMessage());
			}
		}
	}
}
