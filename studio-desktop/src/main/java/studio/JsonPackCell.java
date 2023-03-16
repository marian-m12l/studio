package studio;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import javax.swing.DefaultListModel;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.ListCellRenderer;
import javax.swing.UIManager;
import javax.swing.border.Border;
import javax.swing.plaf.UIResource;

import studio.database.JsonPack;
import studio.database.LocalizedInfos;


class JsonPackCell implements ListCellRenderer<JsonPack>, UIResource {
	
	private static final long serialVersionUID = -9095012373460466070L;
	
	
	private static final Border noBorder = UIManager.getBorder("List.cellNoFocusBorder");
	private static final Border selectedAndFocusedBorder = UIManager.getBorder("List.focusSelectedCellHighlightBorder");
	private static final Border focusBorder = UIManager.getBorder("List.focusCellHighlightBorder");
		
		JsonPackCell() {
			super();
			initialize();
			
		}
		
		
		private JPanel initialize() {
			JPanel res = new JPanel();
			GridBagLayout gridBagLayout = new GridBagLayout();
			gridBagLayout.columnWeights = new double[]{0.0, 1.0};
			res.setLayout(gridBagLayout);
			
			JLabel icon = new JLabel();
			icon.setName("icon");
			icon.setPreferredSize(new Dimension(64, 64));
			GridBagConstraints gbc_icon = new GridBagConstraints();
			gbc_icon.gridheight = 3;
			gbc_icon.insets = new Insets(5, 5, 5, 5);
			gbc_icon.gridx = 0;
			gbc_icon.gridy = 0;
			res.add(icon, gbc_icon);
			
			JLabel title = new JLabel();
			title.setText("title");
			title.setName("title");
			GridBagConstraints gbc_title = new GridBagConstraints();
			gbc_title.anchor = GridBagConstraints.WEST;
			gbc_title.insets = new Insets(5, 5, 5, 5);
			gbc_title.gridx = 1;
			gbc_title.gridy = 0;
			res.add(title, gbc_title);
			
			JLabel subTitle = new JLabel();
			subTitle.setText("subTitle");
			subTitle.setName("subtitle");
			GridBagConstraints gbc_subTitle = new GridBagConstraints();
			gbc_subTitle.anchor = GridBagConstraints.WEST;
			gbc_subTitle.insets = new Insets(0, 5, 5, 5);
			gbc_subTitle.gridx = 1;
			gbc_subTitle.gridy = 1;
			res.add(subTitle, gbc_subTitle);
			
			
			JLabel ageLabel = new JLabel();
			ageLabel.setText("age");
			ageLabel.setName("age");
			GridBagConstraints gbc_ageLabel = new GridBagConstraints();
			gbc_ageLabel.anchor = GridBagConstraints.WEST;
			gbc_ageLabel.insets = new Insets(0 , 5, 5, 5);
			gbc_ageLabel.gridx = 1;
			gbc_ageLabel.gridy = 2;
			res.add(ageLabel, gbc_ageLabel);
			
			res.setPreferredSize(new Dimension(412, 64));
			
			return res;
		}

		@Override
		public Component getListCellRendererComponent(JList<? extends JsonPack> list, JsonPack value, int index,
				boolean isSelected, boolean cellHasFocus) {
			
			JPanel res = this.initialize();
	        res.setComponentOrientation(list.getComponentOrientation());

	        Color bg = null;
	        Color fg = null;

	        JList.DropLocation dropLocation = list.getDropLocation();
	        if (dropLocation != null
	                && !dropLocation.isInsert()
	                && dropLocation.getIndex() == index) {

	        	bg = (Color)UIManager.get("List.dropCellBackground");
	        	fg = (Color)UIManager.get("List.dropCellForeground");

	            isSelected = true;
	        }

	        if (isSelected) {
	            res.setBackground(bg == null ? list.getSelectionBackground() : bg);
	            res.setForeground(fg == null ? list.getSelectionForeground() : fg);
	        }
	        else {
	        	if ( index % 2 == 0 ) {
	        		res.setBackground(list.getBackground());
	        	} else {
	        		res.setBackground(new Color(240, 240, 240));
	        	}
	        	
	        	res.setForeground(list.getForeground());
	        }
			
			 Border border = null;
	        if (cellHasFocus) {
	            if (isSelected) {
	                border = selectedAndFocusedBorder;
	             }
	            if (border == null) {
	                border = focusBorder;
	            }
	        } else {
	            border = noBorder;
	        }
	        res.setBorder(border);
	        
	        
			
			LocalizedInfos defaultLocalizedInfos = value.getLocalizedInfos().values().iterator().next(); 
			List<Component> components = List.of(res.getComponents());
			
			components.stream().filter( (c) -> "icon".equals(c.getName())).findFirst().ifPresent( ( c) -> {
					if ( defaultLocalizedInfos.hasFetchImage() ) {
						((JLabel)c).setIcon(new ImageIcon(defaultLocalizedInfos.getThumbnail()));
					} else {
						
					CompletableFuture.runAsync(() -> {
						defaultLocalizedInfos.getThumbnail();
						DefaultListModel<JsonPack> model = (DefaultListModel<JsonPack>) list.getModel();
						model.setElementAt(value, index);
					});
					}
					
			});
			//icon.setData(defaultLocalizedInfos);
		
						
			
			components.stream().filter( (c) -> "title".equals(c.getName())).findFirst().ifPresent( ( c) -> {
				String strTitle = value.getTitle();
				if ( strTitle == null ) strTitle = defaultLocalizedInfos.getTitle();
				((JLabel)c).setText(strTitle);
			});
			//title.setText(strTitle);
			
			components.stream().filter( (c) -> "subtitle".equals(c.getName())).findFirst().ifPresent( ( c) -> {
				String strSubTitle = value.getSubtitle();
				if ( strSubTitle == null ) strSubTitle = defaultLocalizedInfos.getSubtitle();
				((JLabel)c).setText(strSubTitle);
			});
			
			components.stream().filter( (c) -> "age".equals(c.getName())).findFirst().ifPresent( ( c) -> {
				String strAge = value.getAgeMax() == -1 ? "From " + value.getAgeMin() + " years": "From " + value.getAgeMin() + " to " + value.getAgeMax() + " years";
				((JLabel)c).setText(strAge);
			});
			//subTitle.setText(strSubTitle);
			 
			
			return res;
		}
		
	}