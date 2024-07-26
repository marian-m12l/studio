package studio;

import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

import javax.swing.JTextField;

public class PlaceholderTextField extends JTextField {

	private static final long serialVersionUID = 7150729146333944077L;

	
	private String placeHolder;


	public String getPlaceHolder() {
		return placeHolder;
	}


	public void setPlaceHolder(String placeHolder) {
		this.placeHolder = placeHolder;
	}
	
	@Override
	protected void paintComponent(Graphics g) {
		super.paintComponent(g);
		
		if (placeHolder == null || placeHolder.length() == 0 || getText().length() > 0) {
            return;
        }
		
		final Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(
            RenderingHints.KEY_ANTIALIASING,
            RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setColor(getDisabledTextColor());
        g2.drawString(placeHolder, getInsets().left, g.getFontMetrics().getMaxAscent() + getInsets().top);
	}
}
