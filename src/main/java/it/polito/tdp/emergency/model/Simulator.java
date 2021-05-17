package it.polito.tdp.emergency.model;

import java.time.Duration;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.PriorityQueue;

import it.polito.tdp.emergency.model.Event.EventType;
import it.polito.tdp.emergency.model.Patient.ColorCode;

public class Simulator {

	//coda eventi
	private PriorityQueue<Event> queue;
	
	//modello mondo
	private List<Patient> patients;
	private PriorityQueue<Patient> waitingRoom;
		//contiene solo i pazienti in attesa
	
	private int freeStudios; // numero studi liberi
	private Patient.ColorCode ultimoColore;
	
	//parametri input
	private int totalStudios = 3; //NS
	
	private int numPatiens = 120; //NP
	private Duration T_ARRIVAL = Duration.ofMinutes(5);
	
	private Duration DURATION_TRIAGE = Duration.ofMinutes(5);
	private Duration DURATION_WHITE = Duration.ofMinutes(10);
	private Duration DURATION_YELLOW = Duration.ofMinutes(15);
	private Duration DURATION_RED = Duration.ofMinutes(30);
	
	private Duration TIMEOUT_WHITE = Duration.ofMinutes(60);
	private Duration TIMEOUT_YELLOW = Duration.ofMinutes(30);
	private Duration TIMEOUT_RED = Duration.ofMinutes(30);
	
	private LocalTime startTime = LocalTime.of(8, 00);
	private LocalTime endTime = LocalTime.of(20,00);
	
	//parametri output
	private int patientsTreated;
	private int patientsAbandoned;
	private int patientsDead;
	
	//per inizializzare simulatore e crea eventi iniziali
	public void init() {
		//inizializza coda eventi 
		this.queue = new PriorityQueue<>();
		
		//inizializza modello del mondo
		this.patients = new ArrayList<>();
		this.waitingRoom = new PriorityQueue<>();
		this.freeStudios = this.totalStudios;
		this.ultimoColore = ColorCode.RED;
		
		//inizializzo parametri output
		this.patientsAbandoned = 0;
		this.patientsDead = 0;
		this.patientsTreated = 0;
		
		//inietta eventi di input (ARRIVAL)
		
		LocalTime ora = this.startTime;
		int inseriti = 0;
		
		this.queue.add(new Event(ora, EventType.TICK, null));
		
		while(ora.isBefore(this.endTime) && inseriti< this.numPatiens) {
			Patient p = new Patient(inseriti, ora, ColorCode.NEW);
			
			Event e = new Event(ora, EventType.ARRIVAL, p);
			
			this.queue.add(e);
			this.patients.add(p);
			
			ora = ora.plus(T_ARRIVAL);
			inseriti++;
		}
		

	}
	
	
	private Patient.ColorCode prossimoColore(){
		if(ultimoColore.equals(ColorCode.WHITE)) {
			ultimoColore = ColorCode.YELLOW;
		}else if(ultimoColore.equals(ColorCode.YELLOW)) {
			ultimoColore = ColorCode.RED;
		}else {
			ultimoColore = ColorCode.WHITE;
		}
		return ultimoColore;
	}
	
	public void run() {
		while(!this.queue.isEmpty()) {
			Event e = this.queue.poll();
			System.out.println(e);
			processEvent(e);
		}
	}
	
	public void processEvent(Event e) {
		
		Patient p = e.getPatient();
		LocalTime ora = e.getTime();
		Patient.ColorCode colore;
		
		
		switch(e.getType()) {
		case ARRIVAL:
			this.queue.add(new Event(ora.plus(DURATION_TRIAGE), EventType.TRIAGE, p));
			break;
			
		case TRIAGE:
			colore = p.getColor();
			p.setColor(prossimoColore());
			if(colore.equals(Patient.ColorCode.WHITE))
				this.queue.add(new Event(ora.plus(TIMEOUT_WHITE), EventType.TIMEOUT, p));
			else if(colore.equals(Patient.ColorCode.YELLOW))
				this.queue.add(new Event(ora.plus(TIMEOUT_YELLOW), EventType.TIMEOUT, p));
			else
				this.queue.add(new Event(ora.plus(TIMEOUT_RED), EventType.TIMEOUT, p));
			this.waitingRoom.add(p);
			break;
			
		case TIMEOUT:
			colore = p.getColor();
			switch(colore) {
			case WHITE:
				this.waitingRoom.remove(p);
				p.setColor(ColorCode.OUT);
				this.patientsAbandoned++;
				break;
				
			case YELLOW:
				this.waitingRoom.remove(p);
				p.setColor(ColorCode.RED);
				this.queue.add(new Event(ora.plus(TIMEOUT_RED), EventType.TIMEOUT, p));
				this.waitingRoom.add(p); // la priority queue non cambia la priorità cambiando colore quindi bisogna rimuovere e reinserire
				break;
				
			case RED:
				this.waitingRoom.remove(p);
				p.setColor(ColorCode.BLACK);
				this.patientsDead++;
				break;
				
			default:
//				System.out.println("ERRORE: TIMEOUT CON COLORE "+colore);
			}
			
			break;
			
		case FREE_STUDIO:
			if(this.freeStudios == 0) {
				return; //indifferente rispetto a break perchè dopo lo switch
			}
			//scegliere quale paziente ha diritto di entrare
			Patient primo = this.waitingRoom.poll();
			if(primo != null) {
				//ammetti paziente nello studio
				if(primo.getColor().equals(ColorCode.WHITE))
					this.queue.add(new Event(ora.plus(DURATION_WHITE), EventType.TREATED, primo));
				if(primo.getColor().equals(ColorCode.YELLOW))
					this.queue.add(new Event(ora.plus(DURATION_YELLOW), EventType.TREATED, primo));
				if(primo.getColor().equals(ColorCode.RED))
					this.queue.add(new Event(ora.plus(DURATION_RED), EventType.TREATED, primo));

				primo.setColor(ColorCode.TREATING);
				this.freeStudios--;	
			}
			break;
			
		case TREATED:
			this.patientsTreated++;
			p.setColor(ColorCode.OUT);
			this.freeStudios++;
			this.queue.add(new Event(ora, EventType.FREE_STUDIO, null));
			break;
			
		case TICK:
			if(this.freeStudios>0 && !this.waitingRoom.isEmpty()) {
				this.queue.add(new Event(ora, EventType.FREE_STUDIO, null));
			}
			if(ora.isBefore(this.endTime)) {
				this.queue.add(new Event(ora.plus(Duration.ofMinutes(5)), EventType.TICK, null));	
			}

			break;
		}
	}

	public void setTotalStudios(int totalStudios) {
		this.totalStudios = totalStudios;
	}

	public void setNumPatiens(int numPatiens) {
		this.numPatiens = numPatiens;
	}

	public void setT_ARRIVAL(Duration t_ARRIVAL) {
		T_ARRIVAL = t_ARRIVAL;
	}

	public void setDURATION_TRIAGE(Duration dURATION_TRIAGE) {
		DURATION_TRIAGE = dURATION_TRIAGE;
	}

	public void setDURATION_WHITE(Duration dURATION_WHITE) {
		DURATION_WHITE = dURATION_WHITE;
	}

	public void setDURATION_YELLOW(Duration dURATION_YELLOW) {
		DURATION_YELLOW = dURATION_YELLOW;
	}

	public void setDURATION_RED(Duration dURATION_RED) {
		DURATION_RED = dURATION_RED;
	}

	public void setTIMEOUT_WHITE(Duration tIMEOUT_WHITE) {
		TIMEOUT_WHITE = tIMEOUT_WHITE;
	}

	public void setTIMEOUT_YELLOW(Duration tIMEOUT_YELLOW) {
		TIMEOUT_YELLOW = tIMEOUT_YELLOW;
	}

	public void setTIMEOUT_RED(Duration tIMEOUT_RED) {
		TIMEOUT_RED = tIMEOUT_RED;
	}

	public void setStartTime(LocalTime startTime) {
		this.startTime = startTime;
	}

	public void setEndTime(LocalTime endTime) {
		this.endTime = endTime;
	}

	public int getPatientsTreated() {
		return patientsTreated;
	}

	public int getPatientsAbandoned() {
		return patientsAbandoned;
	}

	public int getPatientsDead() {
		return patientsDead;
	}
	
	
}
