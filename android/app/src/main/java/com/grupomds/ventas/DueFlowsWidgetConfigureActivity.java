package com.grupomds.ventas;

import android.app.Activity;
import android.app.AlertDialog;
import android.appwidget.AppWidgetManager;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Configura cada widget de flujos de forma independiente. */
public class DueFlowsWidgetConfigureActivity extends Activity {
    private int widgetId = AppWidgetManager.INVALID_APPWIDGET_ID;
    private Spinner pipelineInput;
    private Button stagesButton;
    private Button saveButton;
    private TextView status;
    private List<WidgetApi.Board> boards = new ArrayList<>();
    private final Set<Integer> selectedStages = new HashSet<>();

    @Override protected void onCreate(@Nullable Bundle state) {
        super.onCreate(state);
        setResult(RESULT_CANCELED);
        setContentView(R.layout.activity_due_flows_widget_config);
        widgetId = getIntent().getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID);
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) { finish(); return; }
        pipelineInput = findViewById(R.id.due_widget_pipeline);
        stagesButton = findViewById(R.id.due_widget_stages);
        saveButton = findViewById(R.id.due_widget_save);
        status = findViewById(R.id.due_widget_status);
        stagesButton.setEnabled(false); saveButton.setEnabled(false);
        pipelineInput.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) { selectedStages.clear(); updateStagesButton(); }
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent) { }
        });
        stagesButton.setOnClickListener(view -> chooseStages());
        saveButton.setOnClickListener(view -> save());
        load();
    }

    private void load() {
        status.setText("Cargando tablones…");
        new Thread(() -> {
            try {
                List<WidgetApi.Board> loaded = WidgetApi.loadDueOptions();
                runOnUiThread(() -> {
                    boards = loaded;
                    if (boards.isEmpty()) { status.setText("No hay tablones disponibles. Abre MDS Ventas e inicia sesión."); return; }
                    ArrayAdapter<WidgetApi.Board> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, boards);
                    pipelineInput.setAdapter(adapter);
                    int saved = WidgetUpdater.duePipeline(this, widgetId);
                    for (int index = 0; index < boards.size(); index++) if (boards.get(index).id == saved) pipelineInput.setSelection(index);
                    selectedStages.addAll(WidgetUpdater.dueStages(this, widgetId));
                    updateStagesButton(); stagesButton.setEnabled(true); saveButton.setEnabled(true); status.setText("");
                });
            } catch (Exception error) { runOnUiThread(() -> status.setText("No se pudieron cargar los tablones. Abre MDS Ventas e inicia sesión e inténtalo otra vez.")); }
        }).start();
    }

    private WidgetApi.Board currentBoard() { return boards.isEmpty() ? null : boards.get(Math.max(0, pipelineInput.getSelectedItemPosition())); }
    private void updateStagesButton() {
        WidgetApi.Board board=currentBoard(); if(board==null)return;
        int total=board.stages.size(),selected=0;for(WidgetApi.Stage stage:board.stages)if(selectedStages.contains(stage.id))selected++;
        stagesButton.setText(selected==0||selected==total?"Todas las columnas":selected+" columna(s) seleccionada(s)");
    }
    private void chooseStages() {
        WidgetApi.Board board=currentBoard();if(board==null)return;String[] names=new String[board.stages.size()];boolean[] checks=new boolean[names.length];for(int index=0;index<names.length;index++){WidgetApi.Stage stage=board.stages.get(index);names[index]=stage.name;checks[index]=selectedStages.contains(stage.id);}
        new AlertDialog.Builder(this).setTitle("Columnas del tablón").setMultiChoiceItems(names,checks,(dialog,index,checked)->{if(checked)selectedStages.add(board.stages.get(index).id);else selectedStages.remove(board.stages.get(index).id);}).setNegativeButton("Cancelar",null).setPositiveButton("Aplicar",(dialog,which)->updateStagesButton()).show();
    }
    private void save() {
        WidgetApi.Board board=currentBoard();if(board==null)return;WidgetUpdater.saveDueConfig(this,widgetId,board.id,selectedStages);AppWidgetManager manager=AppWidgetManager.getInstance(this);WidgetUpdater.updateDueWidgets(this,manager,new int[]{widgetId});Intent result=new Intent();result.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,widgetId);setResult(RESULT_OK,result);finish();
    }
}
