import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;

class Worker extends Thread{
	Socket clientSoc;
	CANNode node;
	
	public Worker(Socket socket, CANNode node) {
		this.clientSoc = socket;
		this.node = node;
	}
	
	@SuppressWarnings("unchecked")
	@Override
	public void run() {
		try {
			ObjectInputStream in = new ObjectInputStream(clientSoc.getInputStream());
			
			Requests request = null;
			while((request = (Requests) in.readObject())!=null){
				//System.out.println("request is to :"+request.toString());
				if(request.request == 1 || request.request == 4){
					String joinerIP = (String)in.readObject();
					Point point = (Point)in.readObject();
					handleJoin(joinerIP,point);
				}
				else if(request.request == 2){
					String updaterIP = (String)in.readObject();
					String updaterVID = (String)in.readObject();
					node.getMyNeighbours().put(updaterIP, updaterVID);
					//System.out.println("My new neighbours :");
					//System.out.println(node.getMyNeighbours());
				}
				else if(request.request == 3){
					String removeIP = (String)in.readObject();
					node.getMyNeighbours().remove(removeIP);
					//System.out.println("My new neighbours :");
					//System.out.println(node.getMyNeighbours());
				}
				else if(request.request == 5){
					String oldVID = node.getMyVID();
					String newVID = oldVID.substring(0, oldVID.length()-1);
					if(newVID.length() == 0)
						newVID = "999";
					node.setMyVID(newVID);
					Map<String, String> leaverNeighbours = (HashMap<String, String>)in.readObject();
					computeMyNewNeighbours(oldVID,leaverNeighbours);
					updateMyVIDtoNeighbours(newVID);
				}
				else if(request.request == 6){
					String siblingIP = getMySibling();
					node.transferControlToSibling(siblingIP);
					node.removeMyVIDFromNeighbours();
					String newVID = (String)in.readObject();
					node.setMyVID(newVID);
					Map<String, String> leaverNeighbours = (HashMap<String, String>)in.readObject();
					node.setMyNeighbours(leaverNeighbours);
					node.getMyNeighbours().remove(CANNode.myIpaddress);
					node.updateMyNeighbours(newVID);
				}
				break;
			}

		} catch (IOException | ClassNotFoundException e) {
			e.printStackTrace();
		}
	}

	private String getMySibling(){
		String siblingIP = null;
		for(Map.Entry<String, String> entry : 
			node.getMyNeighbours().entrySet()){
			if(node.isMySibling(entry.getValue())){
				siblingIP = entry.getKey();
				break;
			}
		}
		return siblingIP;
	}
	
	private void computeMyNewNeighbours(String oldVID, Map<String, String> leaverNeighbours) {
		for(Map.Entry<String, String> entry : 
			leaverNeighbours.entrySet()){
			if(!entry.getValue().equals(oldVID))
				node.getMyNeighbours().put(entry.getKey(),entry.getValue());
		}
	}

	private void handleJoin(String joinerIP, Point point) {
		String joinerVID;
		Map<String, String> joinerNeighbours = new HashMap<>();
		if(isPointInMyZone(point)){
			if(node.getMyVID().equals("999")){
				node.setMyVID("0");
				joinerVID = "1";
				node.getMyNeighbours().put(joinerIP, joinerVID);
				joinerNeighbours.put(CANNode.myIpaddress, node.getMyVID());
			}
			else{
				joinerVID =  node.getMyVID() + "1";
				node.setMyVID(node.getMyVID() + "0");
				computeJoinerNeighbours(joinerNeighbours,joinerVID);
				updateMyVIDtoNeighbours(node.getMyVID());
				node.getMyNeighbours().put(joinerIP, joinerVID);
				joinerNeighbours.put(CANNode.myIpaddress, node.getMyVID());
			}
			 try {
				Socket joinerSoc = new Socket(joinerIP,8001);
				ObjectOutputStream out = new ObjectOutputStream(joinerSoc.getOutputStream());
				out.writeObject(joinerVID);
				out.writeObject(joinerNeighbours);
				out.close();
				joinerSoc.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		else
			forwardJoinRequest(joinerIP, point);
	}

	
	private void forwardJoinRequest(String joinerIP, Point point) {
		String ipaddress = computeClosestNodeIP(point);
		Socket socket;
		try {
			socket = new Socket(ipaddress, 8001);
			ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
			out.writeObject(Requests.FORWARD_JOIN);
			out.writeObject(joinerIP);
			out.writeObject(point);
			out.close();
			socket.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	
	private String computeClosestNodeIP(Point point) {
		double MIN_DIST = 100, MID_X, MID_Y ;
		String closestNode = null;
		for(Map.Entry<String, String> entry : 
			node.getMyNeighbours().entrySet()){
			Zone neighbourZone = convertVIDToZone(entry.getValue());
			MID_X = (neighbourZone.x2 + neighbourZone.x1)/2;
			MID_Y = (neighbourZone.y2 + neighbourZone.y1)/2;
			double dist = Math.sqrt(Math.pow(MID_X-point.getX(),2)+
					Math.pow(MID_Y-point.getY(),2));
			if(MIN_DIST > dist){
				MIN_DIST = dist;
				closestNode = entry.getKey();
			}
		}
		return closestNode;
	}

	private boolean isPointInMyZone(Point point) {
		if(node.getMyVID().equals("999"))
			return true;
		
		Zone myZone = convertVIDToZone(node.getMyVID());
		
		if((point.getX()>=myZone.x1 && point.getX()<=myZone.x2) &&
				(point.getY()>=myZone.y1 && point.getY()<=myZone.y2))
			return true;
		
		return false;
	}
	
	private void computeJoinerNeighbours(Map<String, String> joinerNeighbours,
			String joinerVID) {
		for(Map.Entry<String, String> entry : 
			node.getMyNeighbours().entrySet()){
			if(hasSharedAnEdge(entry.getValue(),joinerVID))
				joinerNeighbours.put(entry.getKey(),entry.getValue());
		}
	}

	private void updateMyVIDtoNeighbours(String myVID) {
		synchronized(node.getMyNeighbours()){
			Iterator<Entry<String, String>> it = node.getMyNeighbours().entrySet().iterator();
			while (it.hasNext()) {
		        Map.Entry<String, String> entry = it.next();
		        try {
					Socket socket = new Socket(entry.getKey(), 8001);
					ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
					if(hasSharedAnEdge(entry.getValue(),myVID)){
						out.writeObject(Requests.UPDATE_VID);
						out.writeObject(CANNode.myIpaddress);
						out.writeObject(node.getMyVID()); 
					}
					else{
						it.remove();
						out.writeObject(Requests.REMOVE_VID);
						out.writeObject(CANNode.myIpaddress);
					}
					out.close();
					socket.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
		    }
		}
	}
	
	private boolean hasSharedAnEdge(String neighbourVID, String myVID) {
		Zone neighbourZone = convertVIDToZone(neighbourVID);
		Zone myZone = convertVIDToZone(myVID);
		
		if (myZone.x1 <= neighbourZone.x2 && myZone.x2 >= neighbourZone.x1 && 
				myZone.y1 <= neighbourZone.y2 && myZone.y2 >= neighbourZone.y1 &&
				!(myZone.x2 == neighbourZone.x1 && myZone.y2 == neighbourZone.y1) &&
				!(myZone.x1 == neighbourZone.x2 && myZone.y2 == neighbourZone.y1) && 
				!(myZone.x2 == neighbourZone.x1 && myZone.y1 == neighbourZone.y2) &&
				!(myZone.x1 == neighbourZone.x2 && myZone.y1 == neighbourZone.y2)) 
			return true;
		
		return false;
	}

	private Zone convertVIDToZone(String VID) {
		double x1=0, x2=10, y1=0, y2=10;
		for(int i=0;i<VID.length();i+=2){
			if(VID.charAt(i) == '0')
				x2=(x1+x2)/2;
			else
				x1=(x1+x2)/2;
		}
		for(int i=1;i<VID.length();i+=2){
			if(VID.charAt(i) == '0')
				y2=(y1+y2)/2;
			else
				y1=(y1+y2)/2;
		}
		return new Zone(x1, y1, x2, y2);
	}
	
}