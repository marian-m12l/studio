package studio;

import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import javax.swing.DefaultListModel;
import javax.swing.JList;
import javax.swing.TransferHandler;

import studio.database.JsonPack;

public class DeviceListTransferHandler extends TransferHandler {

	private JList libraryPacksList;
	private JList devicePacksList;
	
	public DeviceListTransferHandler(JList libraryList, JList deviceList) {
		super();
		this.libraryPacksList = libraryList;
		this.devicePacksList = deviceList;
	}
	
	private static final long serialVersionUID = -6050701764610620507L;

	public boolean canImport(TransferHandler.TransferSupport support) {
		if (!support.isDataFlavorSupported(DataFlavor.stringFlavor)) {
			return false;
		}
		if (!support.getComponent().getName().equals("devices")) {
			return false;
		}

		JList.DropLocation dl = (JList.DropLocation) support.getDropLocation();
		return dl.getIndex() != -1;
	}

	public boolean importData(TransferHandler.TransferSupport support) {
		if (!canImport(support)) {
			return false;
		}

		Transferable transferable = support.getTransferable();
		String transferMe;
		try {
			transferMe = (String) transferable.getTransferData(DataFlavor.stringFlavor);
		} catch (UnsupportedFlavorException | IOException e) {
			e.printStackTrace();
			return false;
		}

		String source = transferMe.substring(0, transferMe.indexOf(": "));
		List<String> uuids = List.of(transferMe.substring(transferMe.indexOf(": ") + 2).split(","));

		JList<JsonPack> list = source.equals("library") ? libraryPacksList
				: source.equals("devices") ? devicePacksList : null;
		if (list == null)
			return false;

		final DefaultListModel<JsonPack> model = ((DefaultListModel<JsonPack>) list.getModel());

		List<Integer> indices = new ArrayList<Integer>();
		for (String uuid : uuids) {
			indices.add(IntStream.range(0, model.getSize()).filter(i -> uuid.equals(model.get(i).getUuid()))
					.findFirst().getAsInt());
		}

		JList.DropLocation dl = (JList.DropLocation) support.getDropLocation();
		final Integer dropTargetIndex = dl.getIndex();

		if (source.equals("devices")) {
			int nbBeforeDropIndex = (int) indices.stream().filter((v) -> v < dropTargetIndex.intValue())
					.count();
			List<JsonPack> objects = new ArrayList<JsonPack>();
			for (int index = indices.size() - 1; index >= 0; index--) {
				objects.add(model.remove(indices.get(index)));
			}

			int insertIndex = dropTargetIndex - nbBeforeDropIndex;

			for (int i = objects.size() - 1; i >= 0; i--) {
				model.add(insertIndex++, objects.get(i));

			}
		} else {
			List<Object> existingPacks = List
					.of(((DefaultListModel<JsonPack>) ((JList<JsonPack>) support.getComponent()).getModel())
							.toArray());
			List<String> existingUUIDs = existingPacks.stream().map(p -> ((JsonPack) p).getUuid()).toList();

			List<JsonPack> toAdd = indices.stream().map(i -> model.get(i))
					.filter(pack -> existingUUIDs.stream().allMatch(s -> !s.equalsIgnoreCase(pack.getUuid())))
					.toList();
			((DefaultListModel<JsonPack>) devicePacksList.getModel()).addAll(dropTargetIndex, toAdd);
		}

		return true;
	}

}
