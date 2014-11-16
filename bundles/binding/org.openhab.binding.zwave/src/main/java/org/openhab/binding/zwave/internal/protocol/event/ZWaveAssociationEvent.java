	/**
	 * ZWave association group received event.
	 * Send from the association members to the binding
	 * Note that multiple events can be required to build up the full list.
	 * 
	 * @author Chris Jackson
	 * @since 1.4.0
	 */
	public class ZWaveAssociationEvent extends ZWaveEvent {

		private int group;
		private List<Association> members = new ArrayList<Association>();
		
		/**
		 * Constructor. Creates a new instance of the ZWaveAssociationEvent
		 * class.
		 * @param nodeId the nodeId of the event. Must be set to the controller node.
		 */
		public ZWaveAssociationEvent(int nodeId, int group) {
			super(nodeId);
			
			this.group = group;
		}

		public int getGroup() {
			return group;
		}

		public List<Association> getMembers() {
			return members;
		}

		public int getMemberCnt() {
			return members.size();
		}

		public void addMembers(group) {
			members.add(group);
		}
	}
